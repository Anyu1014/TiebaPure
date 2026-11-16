import Foundation

@_silgen_name("Decoder_Interface_init") private func amrInit() -> UnsafeMutableRawPointer?
@_silgen_name("Decoder_Interface_Decode") private func amrDecode(_ state: UnsafeMutableRawPointer, _ input: UnsafePointer<UInt8>, _ output: UnsafeMutablePointer<Int16>, _ badFrame: Int32)
@_silgen_name("Decoder_Interface_exit") private func amrExit(_ state: UnsafeMutableRawPointer)

enum PrivateAMRDecoder {
    enum Failure: Error { case invalidAudio }

    static func playableData(_ data: Data) throws -> Data {
        let magic = Data("#!AMR\n".utf8)
        guard data.starts(with: magic) else { return data }
        let bytes = [UInt8](data)
        let sizes = [13, 14, 16, 18, 20, 21, 27, 32, 6, 0, 0, 0, 0, 0, 0, 1]
        guard let state = amrInit() else { throw Failure.invalidAudio }
        defer { amrExit(state) }
        var offset = magic.count
        var pcm = Data()
        var frames = 0
        while offset < bytes.count {
            let header = bytes[offset]
            let size = sizes[Int((header >> 3) & 15)]
            guard header & 0x83 == 0, size > 0, size <= bytes.count - offset, frames < 30_000 else { throw Failure.invalidAudio }
            var samples = [Int16](repeating: 0, count: 160)
            bytes.withUnsafeBufferPointer { input in
                samples.withUnsafeMutableBufferPointer { output in
                    amrDecode(state, input.baseAddress!.advanced(by: offset), output.baseAddress!, header & 4 == 0 ? 1 : 0)
                }
            }
            samples.withUnsafeBytes { pcm.append(contentsOf: $0) }
            offset += size
            frames += 1
        }
        guard frames > 0 else { throw Failure.invalidAudio }
        var wave = Data("RIFF".utf8)
        func append32(_ n: UInt32) { var v = n.littleEndian; withUnsafeBytes(of: &v) { wave.append(contentsOf: $0) } }
        func append16(_ n: UInt16) { var v = n.littleEndian; withUnsafeBytes(of: &v) { wave.append(contentsOf: $0) } }
        append32(UInt32(36 + pcm.count)); wave.append(Data("WAVEfmt ".utf8)); append32(16)
        append16(1); append16(1); append32(8_000); append32(16_000); append16(2); append16(16)
        wave.append(Data("data".utf8)); append32(UInt32(pcm.count)); wave.append(pcm)
        return wave
    }
}
