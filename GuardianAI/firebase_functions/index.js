const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

exports.onAlertCreated = functions.firestore
    .document('alerts/{alertId}')
    .onCreate(async (snap, context) => {
        const alertData = snap.data();
        const userId = alertData.userId;
        const familyId = alertData.familyId;
        const type = alertData.type;

        console.log(`New Alert Detected! Type: ${type}, Family: ${familyId}, User: ${userId}`);

        if (!familyId) {
            console.log("No familyId found, skipping notifications");
            return null;
        }

        // 1. Get all family members with valid FCM tokens
        const membersSnapshot = await admin.firestore()
            .collection('users')
            .where('familyId', '==', familyId)
            .get();

        const tokens = [];
        membersSnapshot.forEach(doc => {
            const member = doc.data();
            // Don't notify the sender, and ensure token exists
            if (doc.id !== userId && member.fcmToken) {
                tokens.push(member.fcmToken);
            }
        });

        if (tokens.length === 0) {
            console.log("No recipient tokens found for this family.");
            return null;
        }

        // 2. Send Push Notification
        const isNotice = type === 'NOTICE';
        const title = isNotice ? `Family Notice: ${alertData.trigger || ''}` : `EMERGENCY: ${type}`;
        const body = isNotice ? "A family member shared a voice notice." : "A family member needs help! Tap to see their location.";

        const message = {
            notification: {
                title: title,
                body: body,
            },
            data: {
                click_action: "FLUTTER_NOTIFICATION_CLICK",
                alertId: context.params.alertId,
                type: type,
                userId: userId
            },
            tokens: tokens,
        };

        try {
            const response = await admin.messaging().sendEachForMulticast(message);
            console.log(`Successfully sent ${response.successCount} notifications.`);
        } catch (error) {
            console.error("Error sending notifications:", error);
        }

        return null; // Keep alert status as ACTIVE
    });
