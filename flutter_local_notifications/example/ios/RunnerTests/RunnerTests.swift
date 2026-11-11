import Flutter
import UserNotifications
import XCTest

@testable import flutter_local_notifications

class RunnerTests: XCTestCase {
  func testShowStopsAfterAttachmentError() {
    assertStopsAfterAttachmentError(method: "show")
  }

  func testZonedScheduleStopsAfterAttachmentError() {
    assertStopsAfterAttachmentError(method: "zonedSchedule")
  }

  func testPeriodicallyShowStopsAfterAttachmentError() {
    assertStopsAfterAttachmentError(method: "periodicallyShow")
  }

  func testPeriodicallyShowWithDurationStopsAfterAttachmentError() {
    assertStopsAfterAttachmentError(method: "periodicallyShowWithDuration")
  }

  private func assertStopsAfterAttachmentError(
    method: String, file: StaticString = #filePath, line: UInt = #line
  ) {
    let plugin = RecordingNotificationsPlugin()
    let invalidAttachment = NSTemporaryDirectory() + UUID().uuidString + ".unsupported"
    let expectedError: NSError
    do {
      _ = try UNNotificationAttachment(identifier: "image",
                                       url: URL(fileURLWithPath: invalidAttachment), options: nil)
      XCTFail("Expected attachment creation to fail", file: file, line: line)
      return
    } catch {
      expectedError = error as NSError
    }
    var arguments: [String: Any] = [
      "id": 3190,
      "title": "Attachment recovery",
      "body": "Notification without an image",
      "payload": "attachment-recovery",
      "scheduledDateTimeISO8601": "2099-01-01T12:00:00.000Z",
      "timeZoneName": "UTC",
      "repeatInterval": 1,
      "repeatIntervalMilliseconds": 3600000,
      "platformSpecifics": [
        "attachments": [["identifier": "image", "filePath": invalidAttachment]]
      ],
    ]
    var results: [Any?] = []

    plugin.handle(FlutterMethodCall(methodName: method, arguments: arguments)) {
      results.append($0)
    }

    XCTAssertEqual(results.count, 1, file: file, line: line)
    let error = results.first.flatMap { $0 as? FlutterError }
    XCTAssertEqual(error?.code, "Error \(expectedError.code)", file: file, line: line)
    XCTAssertEqual(error?.details as? String, expectedError.domain, file: file, line: line)
    XCTAssertEqual(plugin.requestCount, 0, file: file, line: line)

    // The same notification can be retried without the rejected image.
    arguments["platformSpecifics"] = [String: Any]()
    results.removeAll()
    plugin.handle(FlutterMethodCall(methodName: method, arguments: arguments)) {
      results.append($0)
    }

    XCTAssertEqual(results.count, 1, file: file, line: line)
    XCTAssertNil(results.first ?? nil, file: file, line: line)
    XCTAssertEqual(plugin.requestCount, 1, file: file, line: line)
    XCTAssertEqual(plugin.content?.title, "Attachment recovery", file: file, line: line)
    XCTAssertEqual(plugin.content?.userInfo["payload"] as? String,
                   "attachment-recovery", file: file, line: line)
  }
}

private class RecordingNotificationsPlugin: FlutterLocalNotificationsPlugin {
  var requestCount = 0
  var content: UNMutableNotificationContent?

  // Intercept the private Objective-C selector at the notification submission
  // boundary, while still exercising real attachment creation and error handling.
  @objc(addNotificationRequest:content:result:trigger:)
  func recordRequest(
    _ identifier: String, content: UNMutableNotificationContent?,
    result: FlutterResult, trigger: UNNotificationTrigger?
  ) {
    requestCount += 1
    self.content = content
    result(nil)
  }
}
