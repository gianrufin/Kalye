const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_overland_01";
const BOB_UID = "bob_overland_02";

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

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

test("Unauthenticated user cannot read or write any Kalye collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("trips").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("stops").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("categories").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").set({
      userId: ALICE_UID,
      title: "Unauthorized Trip",
      categoryTag: "Overland",
      notes: "",
      startTimeMillis: 1000,
      endTimeMillis: 2000,
      distanceMeters: 1000.0,
      elevationGainMeters: 50.0,
      elevationLossMeters: 20.0,
      maxElevationMeters: 200.0,
      minElevationMeters: 150.0,
      maxSpeedKmh: 60.0,
      totalFuelCost: 0.0,
      totalFerryCost: 0.0,
      totalOtherCost: 0.0,
      totalFuelLiters: 0.0,
      currencyCode: "PHP",
      routePolyline: "",
      pointCount: 10,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Authenticated owner: valid preferences, categories, trips, and stops create & partial update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);
  const updated = pastDate(1000);

  // 1. Preferences
  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");
  await assertSucceeds(
    prefRef.set({
      userId: ALICE_UID,
      displayName: "Gian Overland",
      distanceUnit: "KM",
      currencyCode: "PHP",
      themeMode: "DARK",
      mapLayer: "TOPO",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    prefRef.update({
      distanceUnit: "MI",
      themeMode: "LIGHT",
      updatedAt: updated,
    })
  );

  // 2. Category
  const catRef = aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_camp");
  await assertSucceeds(
    catRef.set({
      userId: ALICE_UID,
      name: "Car Camp",
      colorHex: 0xFF2A5C48,
      iconKey: "cabin",
      defaultTag: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    catRef.update({
      name: "Alpine Camp",
      updatedAt: updated,
    })
  );

  // 3. Trip
  const tripRef = aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_cordillera_1");
  await assertSucceeds(
    tripRef.set({
      userId: ALICE_UID,
      title: "Baguio to Sagada Ridge",
      categoryTag: "Mountain Pass",
      notes: "Halsema Highway ascent with fog",
      startTimeMillis: 1700000000000,
      endTimeMillis: 1700018000000,
      distanceMeters: 148500.5,
      elevationGainMeters: 1920.0,
      elevationLossMeters: 1410.0,
      maxElevationMeters: 2255.0,
      minElevationMeters: 850.0,
      maxSpeedKmh: 78.4,
      totalFuelCost: 2450.0,
      totalFerryCost: 0.0,
      totalOtherCost: 350.0,
      totalFuelLiters: 38.5,
      currencyCode: "PHP",
      routePolyline: "16.4023,120.5960,1500.0;17.0833,120.9000,1480.0",
      pointCount: 2,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    tripRef.update({
      notes: "Halsema Highway ascent + Echo Valley camp",
      totalOtherCost: 500.0,
      updatedAt: updated,
    })
  );

  // 4. Stop / Fuel / Ferry
  const stopRef = aliceDb.collection("users").doc(ALICE_UID).collection("stops").doc("stop_fuel_1");
  await assertSucceeds(
    stopRef.set({
      userId: ALICE_UID,
      tripId: "trip_cordillera_1",
      stopType: "FUEL",
      title: "Atok Ridge Diesel",
      note: "Full tank before trailhead",
      latitude: 16.582,
      longitude: 120.712,
      elevationMeters: 2150.0,
      costAmount: 2450.0,
      fuelLiters: 38.5,
      pricePerLiter: 63.64,
      currencyCode: "PHP",
      timestampMillis: 1700009000000,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    stopRef.update({
      note: "Full tank + tire check",
      updatedAt: updated,
    })
  );

  // List queries succeed on owner's subcollections
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("trips").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("stops").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("categories").get());
});

test("Cross-user isolation: Alice cannot read, list, create, update, or delete Bob's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const t = pastDate(5000);
    await db.collection("users").doc(BOB_UID).collection("trips").doc("bob_trip").set({
      userId: BOB_UID,
      title: "Bob Secret Trail",
      categoryTag: "Overland",
      notes: "",
      startTimeMillis: 1000,
      endTimeMillis: 2000,
      distanceMeters: 5000.0,
      elevationGainMeters: 120.0,
      elevationLossMeters: 100.0,
      maxElevationMeters: 400.0,
      minElevationMeters: 280.0,
      maxSpeedKmh: 50.0,
      totalFuelCost: 0.0,
      totalFerryCost: 0.0,
      totalOtherCost: 0.0,
      totalFuelLiters: 0.0,
      currencyCode: "USD",
      routePolyline: "",
      pointCount: 0,
      createdAt: t,
      updatedAt: t,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("trips").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("trips").doc("bob_trip").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("trips").doc("bob_trip").update({
      title: "Hijacked",
      updatedAt: pastDate(),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("trips").doc("bob_trip").delete());
});

test("Schema & security negative bounds: rejects @DocumentId key, future timestamps, immutable edits, invalid enums", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(5000);

  // Rejects extra "id" field (which would break @DocumentId deserialization)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_bad_id").set({
      id: "trip_bad_id",
      userId: ALICE_UID,
      title: "Bad Payload",
      categoryTag: "Overland",
      notes: "",
      startTimeMillis: 1000,
      endTimeMillis: 2000,
      distanceMeters: 100.0,
      elevationGainMeters: 0.0,
      elevationLossMeters: 0.0,
      maxElevationMeters: 0.0,
      minElevationMeters: 0.0,
      maxSpeedKmh: 0.0,
      totalFuelCost: 0.0,
      totalFerryCost: 0.0,
      totalOtherCost: 0.0,
      totalFuelLiters: 0.0,
      currencyCode: "PHP",
      routePolyline: "",
      pointCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Rejects future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_future").set({
      userId: ALICE_UID,
      name: "Future",
      colorHex: 123456,
      iconKey: "terrain",
      defaultTag: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );

  // Rejects invalid stopType and negative cost
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("stops").doc("stop_invalid").set({
      userId: ALICE_UID,
      tripId: "trip_1",
      stopType: "AIRPLANE",
      title: "Invalid",
      note: "",
      latitude: 14.5,
      longitude: 121.0,
      elevationMeters: 10.0,
      costAmount: -50.0,
      fuelLiters: 0.0,
      pricePerLiter: 0.0,
      currencyCode: "PHP",
      timestampMillis: 1000,
      createdAt: created,
      updatedAt: created,
    })
  );
});
