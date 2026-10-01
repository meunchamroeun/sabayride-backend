# ABA PayWay Integration & Mobile App Setup Guide

This guide explains how ABA PayWay is integrated into **SabayRide Backend** and provides step-by-step instructions on connecting your mobile application (Flutter, React Native, iOS, Android) to process seamless payments via **ABA PAY Deeplink** and **KHQR**.

---

## 1. Credentials & Configuration

### Sandbox Credentials Provided
- **Merchant ID:** `ec479045`
- **Public API Key:** `eaf45f07b874c56d28261bf3574ae9f77d3465d7` (Used as the HMAC secret for signing requests)
- **Purchase API Endpoint:** `https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/purchase`
- **Check Transaction Endpoint:** `https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/check-transaction-2`

### Production Transition
When ready for production:
1. Contact ABA Merchant Acquisition (`paywaysales@ababank.com`).
2. Replace `PAYWAY_MERCHANT_ID` and `PAYWAY_API_KEY` in environment variables.
3. Replace URL prefixes from `checkout-sandbox.payway.com.kh` to `checkout.payway.com.kh`.

---

## 2. Architecture & Payment Flow

```
[ Mobile App ]                  [ SabayRide Backend ]               [ ABA PayWay ]
      |                                  |                                 |
      | 1. POST /bookings/{id}/payment   |                                 |
      |--------------------------------->| 2. Signs HMAC-SHA512            |
      |                                  |    Calls Purchase API           |
      |                                  |-------------------------------->|
      |                                  | 3. Returns KHQR string +        |
      |                                  |    abapay_deeplink              |
      |                                  |<--------------------------------|
      | 4. Returns PaymentIntent         |                                 |
      |<---------------------------------|                                 |
      |                                                                    |
   [ User pays via ABA App or KHQR ]                                       |
      |                                                                    |
      | 5. Polls GET /bookings/{id}/payment                                |
      |--------------------------------->| 6. Calls check-transaction-2    |
      |                                  |-------------------------------->|
      |                                  | 7. Returns status: APPROVED     |
      |                                  |<--------------------------------|
      |                                  | 8. Marks booking CONFIRMED      |
      | 9. Returns status: PAID          |                                 |
      |<---------------------------------|                                 |
```

---

## 3. Backend Endpoints

### 3.1 Initiate Payment
- **Endpoint:** `POST /api/v1/bookings/{bookingId}/payment`
- **Headers:** `Content-Type: application/json`, `Authorization: Bearer <jwt>`
- **Request Body:**
  ```json
  {
    "method": "KHQR" // or "ABA_PAY"
  }
  ```
- **Response (`201 Created`):**
  ```json
  {
    "paymentId": "4bf8be12-...",
    "bookingId": "ad24807b-...",
    "status": "PENDING",
    "amount": "18.00",
    "currency": "USD",
    "method": "KHQR",
    "qrPayload": "00020101021230510016abaakhppxxx@abaa...",
    "qrImage": "data:image/png;base64,iVBOR...",
    "deeplink": "abamobilebank://ababank.com?type=payway&qrcode=0002010102123051...",
    "transactionId": "SRcaec68fe2f42",
    "expiresAt": "2026-10-01T17:45:00Z",
    "paidAt": null,
    "failureReason": null
  }
  ```

### 3.2 Poll Payment Status
- **Endpoint:** `GET /api/v1/bookings/{bookingId}/payment`
- **Description:** Called every 3 seconds while payment screen is open, and immediately when the app returns to foreground from ABA Mobile.
- **Response (`200 OK`):**
  ```json
  {
    "paymentId": "4bf8be12-...",
    "bookingId": "ad24807b-...",
    "status": "PAID",
    "amount": "18.00",
    "currency": "USD",
    "method": "KHQR",
    "qrPayload": "...",
    "deeplink": "...",
    "transactionId": "SRcaec68fe2f42",
    "expiresAt": null,
    "paidAt": "2026-10-01T16:15:30Z",
    "failureReason": null
  }
  ```

### 3.3 Server Callback (Webhook)
- **Endpoint:** `POST /api/v1/payments/callback`
- **Description:** Called by PayWay gateway to inform server of transaction outcome. Verified and logged to `payment_callback_log` table.

---

## 4. Mobile App Setup Guide

### 4.1 iOS Configuration (`Info.plist`)
To allow your app to query and launch the ABA Mobile Banking app, add the URL scheme to `ios/Runner/Info.plist`:

```xml
<key>LSApplicationQueriesSchemes</key>
<array>
    <string>abamobilebank</string>
    <string>https</string>
    <string>http</string>
</array>
```

### 4.2 Android Configuration (`AndroidManifest.xml`)
On Android 11+ (API level 30+), package visibility restrictions require declaring queries in `android/app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <queries>
        <!-- ABA Mobile Bank App Package -->
        <package android:name="com.paygo24.ibank" />
        <!-- Intent for abamobilebank scheme -->
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="abamobilebank" />
        </intent>
    </queries>
    ...
</manifest>
```

---

## 5. Mobile App Implementation Examples

### 5.1 Flutter Implementation Example

#### Dependencies (`pubspec.yaml`)
```yaml
dependencies:
  flutter:
    sdk: flutter
  http: ^1.2.0
  url_launcher: ^6.3.0
  qr_flutter: ^4.1.0 # Optional: To render KHQR code locally
```

#### Payment Screen (`payment_screen.dart`)
```dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'package:url_launcher/url_launcher.dart';
import 'package:qr_flutter/qr_flutter.dart';

class PaymentScreen extends StatefulWidget {
  final String bookingId;
  final String authToken;

  const PaymentScreen({Key? key, required this.bookingId, required this.authToken}) : super(key: key);

  @override
  State<PaymentScreen> createState() => _PaymentScreenState();
}

class _PaymentScreenState extends State<PaymentScreen> with WidgetsBindingObserver {
  Timer? _pollingTimer;
  Map<String, dynamic>? _paymentIntent;
  bool _isLoading = true;
  String _status = "PENDING";

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initiatePayment();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pollingTimer?.cancel();
    super.dispose();
  }

  // Detect when user returns from ABA Mobile app
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkPaymentStatus();
    }
  }

  Future<void> _initiatePayment() async {
    final url = Uri.parse('http://YOUR_API_GATEWAY:8080/api/v1/bookings/${widget.bookingId}/payment');
    final response = await http.post(
      url,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ${widget.authToken}',
      },
      body: jsonEncode({'method': 'KHQR'}),
    );

    if (response.statusCode == 201) {
      setState(() {
        _paymentIntent = jsonDecode(response.body);
        _isLoading = false;
      });
      _startPolling();
    } else {
      // Handle error
      setState(() => _isLoading = false);
    }
  }

  void _startPolling() {
    _pollingTimer?.cancel();
    _pollingTimer = Timer.periodic(const Duration(seconds: 3), (timer) {
      _checkPaymentStatus();
    });
  }

  Future<void> _checkPaymentStatus() async {
    final url = Uri.parse('http://YOUR_API_GATEWAY:8080/api/v1/bookings/${widget.bookingId}/payment');
    final response = await http.get(
      url,
      headers: {'Authorization': 'Bearer ${widget.authToken}'},
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      final currentStatus = data['status'];
      if (currentStatus == 'PAID') {
        _pollingTimer?.cancel();
        setState(() => _status = 'PAID');
        _onPaymentSuccess();
      } else if (currentStatus == 'FAILED') {
        _pollingTimer?.cancel();
        setState(() => _status = 'FAILED');
      }
    }
  }

  Future<void> _payWithAbaApp() async {
    final deeplink = _paymentIntent?['deeplink'];
    if (deeplink == null) return;

    final uri = Uri.parse(deeplink);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } else {
      // Fallback: If ABA app is not installed, open Store or show KHQR
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('ABA Mobile app not detected. Please scan the KHQR code below.')),
      );
    }
  }

  void _onPaymentSuccess() {
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const BookingConfirmedScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    final qrPayload = _paymentIntent?['qrPayload'] ?? '';
    final amount = _paymentIntent?['amount'] ?? '0.00';
    final currency = _paymentIntent?['currency'] ?? 'USD';

    return Scaffold(
      appBar: AppBar(title: const Text('Pay with ABA PayWay')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            Text('Total: $amount $currency', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
            const SizedBox(height: 24),
            // Button to open ABA Mobile directly
            ElevatedButton.icon(
              onPressed: _payWithAbaApp,
              icon: const Icon(Icons.account_balance),
              label: const Text('Pay with ABA Mobile App'),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF005F71), // ABA Teal
                foregroundColor: Colors.white,
                minimumSize: const Size.fromHeight(50),
              ),
            ),
            const SizedBox(height: 24),
            const Row(
              children: [
                Expanded(child: Divider()),
                Padding(padding: EdgeInsets.symmetric(horizontal: 8), child: Text("OR SCAN KHQR")),
                Expanded(child: Divider()),
              ],
            ),
            const SizedBox(height: 24),
            // Dynamic KHQR Rendering
            if (qrPayload.isNotEmpty)
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.redAccent, width: 2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: QrImageView(
                  data: qrPayload,
                  version: QrVersions.auto,
                  size: 220.0,
                ),
              ),
            const SizedBox(height: 12),
            const Text('Scan using ABA Mobile or any Bakong / KHQR Banking App', textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}

class BookingConfirmedScreen extends StatelessWidget {
  const BookingConfirmedScreen({Key? key}) : super(key: key);
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: const [
            Icon(Icons.check_circle, color: Colors.green, size: 80),
            SizedBox(height: 16),
            Text('Payment Successful!', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
            SizedBox(height: 8),
            Text('Your motorbike booking is confirmed.'),
          ],
        ),
      ),
    );
  }
}
```

---

### 5.2 React Native Implementation Example

#### Using `Linking` to Open Deeplink:
```typescript
import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, Linking, Alert } from 'react-native';
import QRCode from 'react-native-qrcode-svg';

export const PaymentScreen = ({ route, navigation }) => {
  const { bookingId, token } = route.params;
  const [paymentIntent, setPaymentIntent] = useState(null);

  useEffect(() => {
    // 1. Initiate payment
    fetch(`http://YOUR_API_GATEWAY:8080/api/v1/bookings/${bookingId}/payment`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ method: 'KHQR' }),
    })
      .then((res) => res.json())
      .then((data) => setPaymentIntent(data));

    // 2. Poll every 3 seconds
    const interval = setInterval(async () => {
      const res = await fetch(`http://YOUR_API_GATEWAY:8080/api/v1/bookings/${bookingId}/payment`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await res.json();
      if (data.status === 'PAID') {
        clearInterval(interval);
        navigation.replace('BookingSuccess');
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [bookingId]);

  const handleOpenAbaApp = async () => {
    if (!paymentIntent?.deeplink) return;
    const canOpen = await Linking.canOpenURL(paymentIntent.deeplink);
    if (canOpen) {
      await Linking.openURL(paymentIntent.deeplink);
    } else {
      Alert.alert('Notice', 'ABA Mobile app not found. Please scan the KHQR code.');
    }
  };

  return (
    <View style={{ flex: 1, padding: 24, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ fontSize: 20, marginBottom: 16 }}>
        Pay: ${paymentIntent?.amount} {paymentIntent?.currency}
      </Text>

      <TouchableOpacity
        onPress={handleOpenAbaApp}
        style={{ backgroundColor: '#005F71', padding: 16, borderRadius: 8, width: '100%', alignItems: 'center' }}
      >
        <Text style={{ color: 'white', fontWeight: 'bold' }}>Open ABA Mobile App</Text>
      </TouchableOpacity>

      <Text style={{ marginVertical: 16 }}>OR SCAN WITH ANY BANK APP</Text>

      {paymentIntent?.qrPayload && (
        <QRCode value={paymentIntent.qrPayload} size={220} />
      )}
    </View>
  );
};
```

---

## 6. Key Rules & Best Practices

1. **Client Never Reports Success:**
   - The mobile app must **never** report "User paid". Payment verification is strictly confirmed by the backend polling `check-transaction-2` or receiving signed server-to-server callbacks.
2. **Handle App Lifecycle Resumed:**
   - When the user completes payment in the ABA app, they switch back to your app. Trigger an immediate `GET /api/v1/bookings/{id}/payment` inside `AppLifecycleState.resumed` (Flutter) or `AppState.addEventListener('change')` (React Native) so the confirmation screen displays instantly without waiting for the next polling tick.
3. **Deeplink Fallback:**
   - Always display the KHQR code alongside the deeplink button. If a user does not have ABA Bank app installed, they can scan the KHQR with Bakong or any Cambodian bank app (ACLEDA, Wing, Sathapana, etc.).
