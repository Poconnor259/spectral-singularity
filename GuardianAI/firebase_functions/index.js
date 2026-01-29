const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

exports.onAlertCreated = functions.firestore
    .document('alerts/{alertId}')
    .onCreate(async (snap, context) => {
        const alertData = snap.data();
        const userId = alertData.userId;
        const type = alertData.type; // "PANIC_BUTTON" or "VOICE_TRIGGER"

        console.log(`New Alert Detected! Type: ${type}, User: ${userId}`);

        // 1. Fetch User Contacts
        // const userDoc = await admin.firestore().collection('users').doc(userId).get();
        // const contacts = userDoc.data().contacts;

        // 2. Send Notifications (Push / SMS / Email)
        const contactsSnapshot = await admin.firestore()
            .collection('users')
            .doc(userId)
            .collection('contacts')
            .get();

        contactsSnapshot.forEach(doc => {
            const contact = doc.data();
            console.log(`[SIMULATION ALERT] Sending to ${contact.name} (${contact.phoneNumber}): "EMERGENCY! ${type} detected"`);
        });

        if (contactsSnapshot.empty) {
            console.log(`[WARNING] No contacts found for user ${userId}`);
        }

        // 3. Update Alert Status to "NOTIFIED"
        return snap.ref.update({ status: 'NOTIFIED' });
    });
