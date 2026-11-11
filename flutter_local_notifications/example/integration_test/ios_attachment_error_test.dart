import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:timezone/data/latest.dart' as tz_data;
import 'package:timezone/timezone.dart' as tz;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  if (!Platform.isIOS) {
    return;
  }

  final FlutterLocalNotificationsPlugin plugin =
      FlutterLocalNotificationsPlugin();
  const int id = 3190;
  const String title = 'Attachment recovery';
  const String body = 'Notification without an image';
  const String payload = 'attachment-recovery';
  const NotificationDetails withoutImage = NotificationDetails(
    iOS: DarwinNotificationDetails(),
  );
  late Directory attachmentDirectory;
  late NotificationDetails withInvalidImage;

  setUpAll(() async {
    tz_data.initializeTimeZones();
    await plugin.initialize(
      settings: const InitializationSettings(
        iOS: IOSInitializationSettings(requestProvisionalPermission: true),
      ),
    );
  });

  setUp(() async {
    attachmentDirectory = await Directory.systemTemp.createTemp('attachment-');
    withInvalidImage = NotificationDetails(
      iOS: DarwinNotificationDetails(
        attachments: <DarwinNotificationAttachment>[
          DarwinNotificationAttachment(
            '${attachmentDirectory.path}/image.unsupported',
          ),
        ],
      ),
    );
  });

  tearDown(() async {
    await plugin.cancel(id: id);
    await attachmentDirectory.delete(recursive: true);
  });

  final Map<String, Future<void> Function(NotificationDetails)> operations =
      <String, Future<void> Function(NotificationDetails)>{
        'show': (NotificationDetails details) => plugin.show(
          id: id,
          title: title,
          body: body,
          payload: payload,
          notificationDetails: details,
        ),
        'zonedSchedule': (NotificationDetails details) => plugin.zonedSchedule(
          id: id,
          title: title,
          body: body,
          payload: payload,
          scheduledDate: tz.TZDateTime.now(
            tz.UTC,
          ).add(const Duration(hours: 1)),
          notificationDetails: details,
          androidScheduleMode: AndroidScheduleMode.exact,
        ),
        'periodicallyShow': (NotificationDetails details) =>
            plugin.periodicallyShow(
              id: id,
              title: title,
              body: body,
              payload: payload,
              repeatInterval: RepeatInterval.hourly,
              notificationDetails: details,
              androidScheduleMode: AndroidScheduleMode.exact,
            ),
        'periodicallyShowWithDuration': (NotificationDetails details) =>
            plugin.periodicallyShowWithDuration(
              id: id,
              title: title,
              body: body,
              payload: payload,
              repeatDurationInterval: const Duration(hours: 1),
              notificationDetails: details,
            ),
      };

  for (final MapEntry<String, Future<void> Function(NotificationDetails)>
      operation
      in operations.entries) {
    testWidgets(
      '${operation.key} reports an attachment error and allows retry',
      (WidgetTester tester) async {
        // Use the real platform channel: mocking it would miss the native crash.
        await expectLater(
          operation.value(withInvalidImage),
          throwsA(
            isA<PlatformException>()
                .having((PlatformException e) => e.code, 'code', 'Error 101')
                .having(
                  (PlatformException e) => e.details,
                  'domain',
                  'UNErrorDomain',
                ),
          ),
        );
        expect(await plugin.pendingNotificationRequests(), isEmpty);

        await operation.value(withoutImage);
        if (operation.key != 'show') {
          final List<PendingNotificationRequest> pending = await plugin
              .pendingNotificationRequests();
          expect(pending, hasLength(1));
          expect(pending.single.id, id);
          expect(pending.single.title, title);
          expect(pending.single.body, body);
          expect(pending.single.payload, payload);
        }
      },
    );
  }
}
