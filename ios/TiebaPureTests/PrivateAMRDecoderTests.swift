import XCTest
@testable import TiebaPure

final class PrivateAMRDecoderTests: XCTestCase {
    func testDecodesAMRAndRejectsMalformedFrames() throws {
        let audio = Data("#!AMR\n".utf8) + Data(repeating: 0x7c, count: 10)
        let wave = try PrivateAMRDecoder.playableData(audio)
        XCTAssertTrue(wave.starts(with: Data("RIFF".utf8)))
        XCTAssertEqual(wave.count, 3_244)
        XCTAssertThrowsError(try PrivateAMRDecoder.playableData(Data("#!AMR\n".utf8) + Data([0x04])))
        XCTAssertThrowsError(try PrivateAMRDecoder.playableData(Data("#!AMR\n".utf8) + Data([0x4c])))
        let other = Data("RIFF-example".utf8)
        XCTAssertEqual(try PrivateAMRDecoder.playableData(other), other)
    }
}
