import CommonCrypto
import CryptoKit
import Foundation
import Network
import Security
import SwiftProtobuf
import zlib

// Only this fixed official WS endpoint is authorized; account credentials never enter the unencrypted bootstrap.
@MainActor
final class OfficialPrivateTransport {
    private let connection: NWConnection
    private let key: Data
    private var sequence: UInt32 = 0
    private(set) var groups: [Tiebapure_Official_Group] = []
    private var buffered = Data()
    private init(key: Data) {
        connection = NWConnection(host: "im.tieba.baidu.com", port: 8000, using: .tcp)
        self.key = key
        connection.start(queue: DispatchQueue(label: "dev.anyu.official-private-message"))
    }
    private func write(_ data: Data) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.send(content: data, completion: .contentProcessed { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            })
        }
    }
    private func chunk(_ maximum: Int) async throws -> Data {
        try Task.checkCancellation()
        return try await withCheckedThrowingContinuation { continuation in
            connection.receive(minimumIncompleteLength: 1, maximumLength: maximum) { data, _, _, error in
                if let error { continuation.resume(throwing: error) }
                else if let data, !data.isEmpty { continuation.resume(returning: data) }
                else { continuation.resume(throwing: PrivateMessageError.invalidResponse) }
            }
        }
    }
    private func read(_ count: Int) async throws -> Data {
        guard count >= 0, count <= 4_194_304 else { throw PrivateMessageError.invalidResponse }
        while buffered.count < count { buffered.append(try await chunk(min(count - buffered.count, 32_768))) }
        let result = Data(buffered.prefix(count)); buffered = Data(buffered.dropFirst(count)); return result
    }
    private func handshake(device: String) async throws {
        let nonce = try Self.random(16).base64EncodedString()
        let request = "GET / HTTP/1.1\r\nHost: im.tieba.baidu.com:8000\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: \(nonce)\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Extensions: im_version=2.3\r\nContent-Type: application/octet-stream\r\ncuid: \(device)\r\n\r\n"
        try await write(Data(request.utf8))
        let delimiter = Data([13,10,13,10])
        while buffered.range(of: delimiter) == nil {
            guard buffered.count < 16_384 else { throw PrivateMessageError.invalidResponse }
            buffered.append(try await chunk(min(4096, 16_384 - buffered.count)))
        }
        guard let range = buffered.range(of: delimiter), let header = String(data: buffered.prefix(range.upperBound), encoding: .ascii) else { throw PrivateMessageError.invalidResponse }
        buffered = Data(buffered.dropFirst(range.upperBound))
        let lines = header.components(separatedBy: "\r\n")
        guard lines.first?.hasPrefix("HTTP/1.1 101 ") == true else { throw PrivateMessageError.invalidResponse }
        var headers: [String: String] = [:]
        for line in lines.dropFirst() where !line.isEmpty {
            guard let colon = line.firstIndex(of: ":") else { throw PrivateMessageError.invalidResponse }
            let key = line[..<colon].lowercased(), value = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
            if let previous = headers[key], ["sec-websocket-accept", "upgrade"].contains(key), previous != value { throw PrivateMessageError.invalidResponse }
            headers[key] = value
        }
        let expected = Data(Insecure.SHA1.hash(data: Data((nonce + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").utf8))).base64EncodedString()
        guard headers["sec-websocket-accept"] == expected, headers["upgrade"]?.lowercased() == "websocket" else { throw PrivateMessageError.invalidResponse }
    }
    private func sendFrame(_ data: Data, opcode: UInt8 = 2) async throws {
        guard data.count <= 4_194_304 else { throw PrivateMessageError.invalidResponse }
        var frame = Data([128 | opcode])
        if data.count < 126 { frame.append(128 | UInt8(data.count)) }
        else if data.count <= 65_535 {
            frame.append(254); var size = UInt16(data.count).bigEndian; withUnsafeBytes(of: &size) { frame.append(contentsOf: $0) }
        } else {
            frame.append(255); var size = UInt64(data.count).bigEndian; withUnsafeBytes(of: &size) { frame.append(contentsOf: $0) }
        }
        let mask = try Self.random(4); frame.append(mask)
        frame.append(contentsOf: data.enumerated().map { $0.element ^ mask[$0.offset % 4] })
        try await write(frame)
    }
    private func receiveFrame() async throws -> Data {
        var result = Data(), started = false
        for _ in 0..<256 {
            let header = try await read(2), opcode = header[0] & 15, final = header[0] & 128 != 0
            guard header[0] & 112 == 0, header[1] & 128 == 0 else { throw PrivateMessageError.invalidResponse }
            let marker = header[1] & 127
            let size: UInt64
            if marker == 126 { size = try await read(2).reduce(UInt64(0)) { ($0 << 8) | UInt64($1) } }
            else if marker == 127 { size = try await read(8).reduce(UInt64(0)) { ($0 << 8) | UInt64($1) } }
            else { size = UInt64(marker) }
            guard size <= 4_194_304 else { throw PrivateMessageError.invalidResponse }
            let bytes = try await read(Int(size))
            switch opcode {
            case 9:
                guard size <= 125, final else { throw PrivateMessageError.invalidResponse }; try await sendFrame(bytes, opcode: 10)
            case 10: guard size <= 125, final else { throw PrivateMessageError.invalidResponse }
            case 2, 0:
                guard (opcode == 2 && !started) || (opcode == 0 && started), result.count + bytes.count <= 4_194_304 else { throw PrivateMessageError.invalidResponse }
                started = true; result.append(bytes); if final { return result }
            default: throw PrivateMessageError.invalidResponse
            }
        }
        throw PrivateMessageError.invalidResponse
    }
    static func pullRequest(device: String, groups: [Tiebapure_Official_Group], cursors: [Tiebapure_Official_Group]) -> Tiebapure_Official_PullRequest {
        let known = Dictionary(cursors.map { ($0.id, $0.lastMsgID) }, uniquingKeysWith: max)
        var request = Tiebapure_Official_PullRequest(); request.cuid = device
        request.data.getType = "2"; request.data.repair.followType = "0"
        // Zero skips private history on the server despite a successful response.
        request.data.groups = groups.map { group in
            var cursor = Tiebapure_Official_GroupCursor(); cursor.groupID = group.id; cursor.lastMsgID = max(known[group.id] ?? 1, 1); return cursor
        }
        return request
    }
    static func derive(_ secret: String) throws -> Data {
        let salt: [UInt8] = [164,11,200,52,214,149,243,19]
        var key = [UInt8](repeating: 0, count: 32)
        let status = secret.withCString { password in CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), password, secret.utf8.count, salt, salt.count, CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA1), 5, &key, key.count) }
        guard status == kCCSuccess else { throw PrivateMessageError.invalidResponse }
        return Data(key)
    }
    static func crypt(_ data: Data, key: Data, encrypt: Bool) throws -> Data {
        guard key.count == 32, data.count <= 4_194_304 else { throw PrivateMessageError.invalidResponse }
        var output = [UInt8](repeating: 0, count: data.count + 16), written = 0
        let status = key.withUnsafeBytes { keyBytes in data.withUnsafeBytes { bytes in
            CCCrypt(CCOperation(encrypt ? kCCEncrypt : kCCDecrypt), CCAlgorithm(kCCAlgorithmAES), CCOptions(kCCOptionECBMode | kCCOptionPKCS7Padding), keyBytes.baseAddress, key.count, nil, bytes.baseAddress, data.count, &output, output.count, &written)
        } }
        guard status == kCCSuccess else { throw PrivateMessageError.invalidResponse }
        return Data(output.prefix(written))
    }
    private static func random(_ count: Int) throws -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        guard SecRandomCopyBytes(kSecRandomDefault, count, &bytes) == errSecSuccess else { throw PrivateMessageError.invalidResponse }
        return Data(bytes)
    }
    private static func wrap(_ secret: String) throws -> Data {
        let encoded = "MIIBCgKCAQEAwQpwBZxXJV/JVRF/uNfyMSdu7YWwRNLM8+2xbniGp2iIQHOikPpTYQjlQgMi1uvq1kZpJ32rHo3hkwjy2l0lFwr3u4Hk2Wk7vnsqYQjAlYlK0TCzjpmiI+OiPOUNVtbWHQiLiVqFtzvpvi4AU7C1iKGvc/4IS45WjHxeScHhnZZ7njS4S1UgNP/GflRIbzgbBhyZ9kEW5/OO5YfG1fy6r4KSlDJw4o/mw5XhftyIpL+5ZBVBC6E1EIiP/dd9AbK62VV1PByfPMHMixpxI3GM2qwcmFsXcCcgvUXJBa9k6zP8dDQ3csCM2QNT+CQAOxthjtp/TFWaD7MzOdsIYb3THwIDAQAB"
        guard let data = Data(base64Encoded: encoded), let publicKey = SecKeyCreateWithData(data as CFData, [kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeyClass: kSecAttrKeyClassPublic] as CFDictionary, nil), let wrapped = SecKeyCreateEncryptedData(publicKey, .rsaEncryptionPKCS1, Data(secret.utf8) as CFData, nil) else { throw PrivateMessageError.invalidResponse }
        return wrapped as Data
    }
    static func session<T>(account: Account, device: String, action: (OfficialPrivateTransport) async throws -> T) async throws -> T {
        guard BaiduCredentialPolicy.isValidCookieValue(account.bduss), BaiduCredentialPolicy.isValidCookieValue(account.stoken), !device.isEmpty, device.utf8.count <= 128, device.utf8.allSatisfy({ (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) || $0 == 45 || $0 == 95 }) else { throw PrivateMessageError.invalidUser }
        let secret = try random(16).map { String(format: "%02x", $0) }.joined()
        let transport = try OfficialPrivateTransport(key: derive(secret))
        let timeout = Task { try await Task.sleep(nanoseconds: 45_000_000_000); transport.connection.cancel() }
        defer { timeout.cancel(); transport.connection.cancel() }
        return try await withTaskCancellationHandler {
            try await transport.handshake(device: device)
            var request = Tiebapure_Official_OnlineRequest()
            request.cuid = device
            request.data.device = String(decoding: try JSONSerialization.data(withJSONObject: ["_client_type":"2", "_client_version":"22.12.1.0", "cuid":device, "pversion":"1.0.3"]), as: UTF8.self)
            request.data.secretKey = try wrap(secret)
            func online(_ encrypted: Bool) async throws -> Tiebapure_Official_OnlineResponse {
                let response = try Tiebapure_Official_OnlineResponse(serializedBytes: await transport.request(1001, data: request.serializedData(), encrypted: encrypted))
                guard response.hasError else { throw PrivateMessageError.invalidResponse }
                guard response.error.code == 0 else { throw PrivateMessageError.rejected(Int(response.error.code)) }
                return response
            }
            _ = try await online(false)
            _ = try await online(true)
            try Task.checkCancellation()
            request.data.bduss = account.bduss
            request.data.stoken = account.stoken
            let authenticated = try await online(true)
            guard String(authenticated.data.user.id) == account.uid else { throw PrivateMessageError.invalidUser }
            transport.groups = authenticated.data.groups
            try Task.checkCancellation()
            return try await action(transport)
        } onCancel: { transport.connection.cancel() }
    }
    func request(_ command: UInt32, data: Data, encrypted: Bool = true) async throws -> Data {
        try Task.checkCancellation()
        guard data.count <= 4_194_288 else { throw PrivateMessageError.invalidResponse }
        sequence += 1
        let requestID = sequence
        var frame = Data([encrypted ? 136 : 8])
        for value in [command, requestID] { var number = value.bigEndian; withUnsafeBytes(of: &number) { frame.append(contentsOf: $0) } }
        frame.append(try encrypted ? Self.crypt(data, key: key, encrypt: true) : data)
        try await sendFrame(frame)
        for _ in 0..<64 {
            try Task.checkCancellation()
            let bytes = try await receiveFrame()
            guard bytes.count >= 9 else { throw PrivateMessageError.invalidResponse }
            let flags = bytes[0]
            let cmd = bytes[1..<5].reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
            let seq = bytes[5..<9].reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
            guard !encrypted || flags & 128 != 0 else { throw PrivateMessageError.invalidResponse }
            var body = Data(bytes.dropFirst(9))
            if flags & 128 != 0 { body = try Self.crypt(body, key: key, encrypt: false) }
            if flags & 64 != 0 { body = try OfficialPrivateCompression.decode(body, kind: 11) }
            if flags & 4 != 0 {
                guard body.count >= 4 else { throw PrivateMessageError.invalidResponse }
                let count = body.prefix(4).reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
                guard count <= body.count - 4 else { throw PrivateMessageError.invalidResponse }
                body = Data(body.dropFirst(4 + Int(count)))
            }
            if cmd == command && seq == requestID { return body }
        }
        throw PrivateMessageError.invalidResponse
    }
}
