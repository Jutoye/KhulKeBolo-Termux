const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const SPEAKER_UID = "speaker_uid_101";
const LISTENER_UID = "listener_uid_202";
const OUTSIDER_UID = "outsider_uid_303";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(SPEAKER_UID).get());
  await assertFails(unauthDb.collection("queue").where("role", "==", "LISTENER").get());
  await assertFails(
    unauthDb.collection("rooms").where("participantUids", "array-contains", SPEAKER_UID).get()
  );
  await assertFails(
    unauthDb.collection("mood_feedback").doc("m1").set({
      mood: "HALKA",
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("Users collection: owner-only read/write, no list, immutable createdAt", async () => {
  const speakerDb = testEnv.authenticatedContext(SPEAKER_UID).firestore();
  const listenerDb = testEnv.authenticatedContext(LISTENER_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    speakerDb.collection("users").doc(SPEAKER_UID).set({
      uid: SPEAKER_UID,
      ageConfirmed: true,
      rulesAcceptedAt: now,
      blockedUids: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    speakerDb.collection("users").doc(SPEAKER_UID).update({
      blockedUids: [OUTSIDER_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read & list must fail
  await assertFails(listenerDb.collection("users").doc(SPEAKER_UID).get());
  await assertFails(speakerDb.collection("users").get());

  // Cannot mutate createdAt or uid
  await assertFails(
    speakerDb.collection("users").doc(SPEAKER_UID).update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Queue collection: self-create, role-filtered list succeeds, unfiltered fails", async () => {
  const speakerDb = testEnv.authenticatedContext(SPEAKER_UID).firestore();
  const listenerDb = testEnv.authenticatedContext(LISTENER_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    listenerDb.collection("queue").doc(LISTENER_UID).set({
      uid: LISTENER_UID,
      role: "LISTENER",
      nickname: "Dost #1122",
      blockedUids: [],
      createdAt: now,
    })
  );

  // Cannot create queue entry for someone else
  await assertFails(
    speakerDb.collection("queue").doc(OUTSIDER_UID).set({
      uid: OUTSIDER_UID,
      role: "SPEAKER",
      nickname: "Dost #9999",
      blockedUids: [],
      createdAt: now,
    })
  );

  // Role-filtered query succeeds; unfiltered query fails
  await assertSucceeds(
    speakerDb.collection("queue").where("role", "==", "LISTENER").get()
  );
  await assertFails(speakerDb.collection("queue").get());
});

test("Rooms & messages: participant isolation, 500-char limit, extension, and end-lock", async () => {
  const speakerDb = testEnv.authenticatedContext(SPEAKER_UID).firestore();
  const listenerDb = testEnv.authenticatedContext(LISTENER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  const startedAt = new Date(Date.now() - 2000);
  const endsAt = new Date(Date.now() + 20 * 60 * 1000);
  const deleteAt = new Date(Date.now() + 80 * 60 * 1000);

  await assertSucceeds(
    speakerDb.collection("rooms").doc("room_abc123").set({
      speakerUid: SPEAKER_UID,
      listenerUid: LISTENER_UID,
      participantUids: [SPEAKER_UID, LISTENER_UID],
      speakerNickname: "Dost #1001",
      listenerNickname: "Dost #2002",
      status: "ACTIVE",
      startedAt,
      endsAt,
      deleteAt,
      extendRequestedBySpeaker: false,
      extendRequestedByListener: false,
      extendedApplied: false,
      updatedAt: startedAt,
    })
  );

  // Participant filtered list succeeds; outsider fails
  await assertSucceeds(
    listenerDb
      .collection("rooms")
      .where("participantUids", "array-contains", LISTENER_UID)
      .get()
  );
  await assertFails(outsiderDb.collection("rooms").doc("room_abc123").get());

  // Valid message <= 500 chars succeeds
  await assertSucceeds(
    speakerDb
      .collection("rooms")
      .doc("room_abc123")
      .collection("messages")
      .doc("msg_1")
      .set({
        roomId: "room_abc123",
        senderUid: SPEAKER_UID,
        senderRole: "SPEAKER",
        participantUids: [SPEAKER_UID, LISTENER_UID],
        text: "Aaj dil bohat halka mehsoos ho raha hai.",
        flagged: false,
        createdAt: new Date(Date.now() - 1000),
      })
  );

  // Oversized message > 500 chars fails
  await assertFails(
    speakerDb
      .collection("rooms")
      .doc("room_abc123")
      .collection("messages")
      .doc("msg_too_long")
      .set({
        roomId: "room_abc123",
        senderUid: SPEAKER_UID,
        senderRole: "SPEAKER",
        participantUids: [SPEAKER_UID, LISTENER_UID],
        text: "a".repeat(501),
        flagged: false,
        createdAt: new Date(Date.now() - 1000),
      })
  );

  // Outsider cannot read specific message, run unfiltered list, or write messages
  await assertFails(
    outsiderDb
      .collection("rooms")
      .doc("room_abc123")
      .collection("messages")
      .doc("msg_1")
      .get()
  );
  await assertFails(
    outsiderDb
      .collection("rooms")
      .doc("room_abc123")
      .collection("messages")
      .get()
  );
  await assertFails(
    outsiderDb
      .collection("rooms")
      .doc("room_abc123")
      .collection("messages")
      .doc("msg_intrude")
      .set({
        roomId: "room_abc123",
        senderUid: OUTSIDER_UID,
        senderRole: "LISTENER",
        participantUids: [OUTSIDER_UID, SPEAKER_UID],
        text: "Intrusion",
        flagged: false,
        createdAt: new Date(Date.now() - 1000),
      })
  );

  // Participant message query aligned with ChatRepository succeeds
  await assertSucceeds(
    listenerDb
      .collection("rooms")
      .doc("room_abc123")
      .collection("messages")
      .where("participantUids", "array-contains", LISTENER_UID)
      .get()
  );

  // Mutual +10m extension step 1 (speaker requests)
  await assertSucceeds(
    speakerDb.collection("rooms").doc("room_abc123").update({
      extendRequestedBySpeaker: true,
      extendRequestedByListener: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // End session transitions to ENDED
  await assertSucceeds(
    listenerDb.collection("rooms").doc("room_abc123").update({
      status: "ENDED",
      endedByUid: LISTENER_UID,
      endedAt: new Date(Date.now() - 400),
      deleteAt: new Date(Date.now() + 60 * 60 * 1000),
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Messages cannot be created after room ENDED; deleting messages succeeds
  await assertFails(
    speakerDb
      .collection("rooms")
      .doc("room_abc123")
      .collection("messages")
      .doc("msg_after_end")
      .set({
        roomId: "room_abc123",
        senderUid: SPEAKER_UID,
        senderRole: "SPEAKER",
        participantUids: [SPEAKER_UID, LISTENER_UID],
        text: "Should fail",
        flagged: false,
        createdAt: new Date(Date.now() - 200),
      })
  );
  await assertSucceeds(
    listenerDb
      .collection("rooms")
      .doc("room_abc123")
      .collection("messages")
      .doc("msg_1")
      .delete()
  );
});

test("Reports, anonymous crisis_events, and mood_feedback rules", async () => {
  const speakerDb = testEnv.authenticatedContext(SPEAKER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    speakerDb.collection("reports").doc("rep_1").set({
      roomId: "room_abc123",
      reporterUid: SPEAKER_UID,
      reportedUid: LISTENER_UID,
      reason: "Mazaak uraya",
      lastMessages: ["LISTENER: haha"],
      createdAt: now,
    })
  );
  await assertFails(outsiderDb.collection("reports").doc("rep_1").get());
  await assertSucceeds(speakerDb.collection("reports").doc("rep_1").delete());

  // Anonymous crisis event & mood feedback are write-only
  await assertSucceeds(
    speakerDb.collection("crisis_events").doc("crisis_1").set({
      roomId: "room_abc123",
      createdAt: now,
    })
  );
  await assertFails(speakerDb.collection("crisis_events").doc("crisis_1").get());

  await assertSucceeds(
    speakerDb.collection("mood_feedback").doc("mood_1").set({
      mood: "HALKA",
      createdAt: now,
    })
  );
  await assertFails(speakerDb.collection("mood_feedback").doc("mood_1").get());
});
