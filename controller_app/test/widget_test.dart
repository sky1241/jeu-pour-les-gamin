import 'package:flutter_test/flutter_test.dart';
import 'package:controller_app/main.dart';

void main() {
  testWidgets('App renders connect screen', (WidgetTester tester) async {
    await tester.pumpWidget(const ControllerApp());
    expect(find.text('SuperTux Controller'), findsOneWidget);
    expect(find.text('Connect'), findsOneWidget);
  });
}
