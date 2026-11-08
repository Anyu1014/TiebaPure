import Foundation
import zlib

enum OfficialPrivateCompression {
    nonisolated static func decode(_ data: Data, kind: UInt8) throws -> Data {
        guard data.count <= 4_194_304, kind == 1 || kind == 11 else { throw PrivateMessageError.invalidResponse }
        if kind == 1 { return data }
        var stream = z_stream()
        guard inflateInit2_(&stream, 31, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size)) == Z_OK else { throw PrivateMessageError.invalidResponse }
        defer { inflateEnd(&stream) }
        return try data.withUnsafeBytes { raw in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: raw.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(data.count)
            var result = Data(), buffer = [UInt8](repeating: 0, count: 8192)
            while true {
                let status = buffer.withUnsafeMutableBytes { bytes -> Int32 in
                    stream.next_out = bytes.bindMemory(to: Bytef.self).baseAddress; stream.avail_out = 8192
                    return inflate(&stream, Z_NO_FLUSH)
                }
                let count = 8192 - Int(stream.avail_out)
                guard result.count + count <= 4_194_304, status == Z_OK || status == Z_STREAM_END else { throw PrivateMessageError.invalidResponse }
                result.append(contentsOf: buffer.prefix(count))
                if status == Z_STREAM_END { return result }
                guard count > 0 else { throw PrivateMessageError.invalidResponse }
            }
        }
    }
}
