import Foundation

@main
struct RouteChecks {
    static func main() {
        if let expected = ProcessInfo.processInfo.environment["EXPECTED_URL_SCHEME"] {
            precondition(ExternalRoute.scheme == expected)
        }
        precondition(ExternalRoute.parse(URL(string: "https://tieba.baidu.com/p/42?pid=7")!) == .thread(id: 42, postID: 7))
        precondition(ExternalRoute.parse(URL(string: "https://tieba.baidu.com.evil.example/p/42")!) == nil)
        precondition(ExternalRoute.parse(URL(string: "http://tieba.baidu.com/p/42")!) == nil)
        precondition(ExternalRoute.parse(URL(string: "tiebapure://thread/42")!) == .thread(id: 42, postID: nil))
        let imported = ExternalRoute.importURL(forWebURL: URL(string: "https://tieba.baidu.com/p/42")!)!
        precondition(imported.scheme == ExternalRoute.scheme)
        precondition(ExternalRoute.parse(imported) == .thread(id: 42, postID: nil))
        precondition(ExternalRoute.parse(URL(string: "tiebapure://thread/18446744073709551615")!) == nil)
        print("PASS: external route validation and round trip")
    }
}
