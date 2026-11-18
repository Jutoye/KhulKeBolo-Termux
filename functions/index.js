const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

const PHONE_REGEX = /(?:\+?92|0092|0)[\s.\-()]*3[\d\s.\-()]{8,13}|(?:\d[\s.\-]*){7,}/;
const SOCIAL_REGEX = /(whatsapp|wa\.me|instagram|insta|facebook|snapchat|telegram|t\.me|https?:\/\/|www\.|@[A-Za-z0-9._]{2,})/i;

/**
 * Server-side backup moderation check when a message is created.
 * Deletes messages that bypass client filters (phone numbers, links, handles).
 */
exports.moderateNewMessage = onDocumentCreated(
  "rooms/{roomId}/messages/{messageId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data();
    const text = (data.text || "").toString();

    if (PHONE_REGEX.test(text) || SOCIAL_REGEX.test(text) || text.length > 500) {
      await snap.ref.delete();
    }
  }
);

/**
 * Deletes all messages when a room transitions to ENDED.
 */
exports.cleanupMessagesOnRoomEnd = onDocumentUpdated(
  "rooms/{roomId}",
  async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();
    if (before.status === "ACTIVE" && after.status === "ENDED") {
      const messagesRef = event.data.after.ref.collection("messages");
      const snap = await messagesRef.get();
      if (snap.empty) return;
      const batch = db.batch();
      snap.docs.forEach((doc) => batch.delete(doc.ref));
      await batch.commit();
    }
  }
);

/**
 * Scheduled hourly cleanup for expired rooms whose deleteAt TTL has passed.
 */
exports.purgeExpiredRooms = onSchedule("every 60 minutes", async () => {
  const now = admin.firestore.Timestamp.now();
  const expiredRooms = await db
    .collection("rooms")
    .where("deleteAt", "<=", now)
    .limit(100)
    .get();

  for (const roomDoc of expiredRooms.docs) {
    const msgs = await roomDoc.ref.collection("messages").get();
    const batch = db.batch();
    msgs.docs.forEach((m) => batch.delete(m.ref));
    batch.delete(roomDoc.ref);
    await batch.commit();
  }
});
