import XCTest
@testable import TiebaPure

final class PrivateReadingPolicyTests: XCTestCase {
    func testBoundaryAndMissingHistory() {
        let position = PrivateReadingPosition(anchor: "2", latest: "2", atBottom: true)
        XCTAssertEqual(PrivateReadingPolicy.boundary(position, ids: ["1", "2", "3"]), 2)
        XCTAssertEqual(PrivateReadingPolicy.target(position, ids: ["1", "2", "3"]), "3")
        XCTAssertNil(PrivateReadingPolicy.target(position, ids: ["1", "2"]))
        XCTAssertNil(PrivateReadingPolicy.boundary(position, ids: ["3", "4"]))
        XCTAssertTrue(PrivateReadingPolicy.needsHistory(position, ids: ["3", "4"], hasMore: true))
    }
    func testMiddleAnchor() {
        let position = PrivateReadingPosition(anchor: "1", latest: "3", atBottom: false)
        XCTAssertEqual(PrivateReadingPolicy.target(position, ids: ["1", "2", "3", "4"]), "1")
        XCTAssertNil(PrivateReadingPolicy.boundary(position, ids: ["1", "2", "3", "4"]))
        XCTAssertTrue(PrivateReadingPolicy.needsHistory(position, ids: ["3", "4"], hasMore: true))
        XCTAssertFalse(PrivateReadingPolicy.needsHistory(position, ids: ["3", "4"], hasMore: false))
    }
}
