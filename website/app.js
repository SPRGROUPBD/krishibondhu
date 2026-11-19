import {
  RTDB_CONFIG,
  SUPERADMIN_CREDENTIALS,
  DEFAULT_AD_CONFIG,
  DEFAULT_DAILY_TIPS,
  DEFAULT_PAYMENT_SETTINGS,
  DEFAULT_FARMER_PAYMENT_SETTINGS,
  APP_VERSION_NAME,
  APP_BUILD_VERSION,
  MON_TO_KG,
  FIXED_ACTIVATION_FEE_BDT,
  FIXED_VALIDITY_YEARS,
  ACCOUNT_STATUS_META,
  ACTIVATION_PAYMENT_STATUS_META,
  ORDER_PAYMENT_STATUS_META,
  MASTER_CATEGORIES,
  BD_DIVISIONS,
  BD_DISTRICTS,
  ORDER_STATUS_META,
  PRODUCT_STATUS_META,
  UNITS,
  BD_DISTRICT_CENTROIDS,
  buildInitialSeedLocations,
  resolveLocationFromCoords,
  isAllowedUnit,
  formatDateBn,
  daysUntilExpiry,
  isMarketplaceActive,
  hashPassword,
  formatBdt,
  formatQty,
  escapeHtml,
  shortId
} from "./data.js";

const STORAGE_KEY = "krishi_bondhu_web_state_v2";
const APP_LAUNCH_TIME = Date.now();
const ASSET_BASE = new URL("./assets/", import.meta.url).href;

const state = {
  lang: "bn",
  page: "home",
  pageParams: {},
  isOnline: typeof navigator !== "undefined" ? navigator.onLine : true,
  rtdbConnected: false,
  activeUserId: "",
  users: [],
  categories: [...MASTER_CATEGORIES],
  locations: buildInitialSeedLocations(),
  products: [],
  orders: [],
  orderPayments: [],
  farmerPaymentSettings: {},
  activationPayments: [],
  paymentSettings: { ...DEFAULT_PAYMENT_SETTINGS },
  notifications: [],
  reviews: [],
  complaints: [],
  auditLogs: [],
  cart: [],
  filters: {
    query: "",
    categoryId: "",
    division: "",
    district: "",
    upazila: "",
    union: "",
    sort: "NEWEST"
  },
  farmerTab: "orders",
  adminTab: "overview",
  adminUserFilter: "ALL",
  adminUserStatusFilter: "ALL",
  adminUserSearch: "",
  adminPaymentFilter: "ALL",
  adminPaymentQuery: "",
  adminOrderFilter: "ALL",
  adminProductFilter: "PENDING_APPROVAL",
  adminProductSearch: "",
  infoTab: "about",
  adConfig: { ...DEFAULT_AD_CONFIG },
  dailyTips: [...DEFAULT_DAILY_TIPS],
  dailyTipsManaged: false,
  dailyTipIndex: 0,
  revealedDailyTipId: "",
  heroSliders: [],
  heroSlideIdx: 0,
  activationMode: "account"
};

let rtdbRef = null;
let rtdbApi = null;
let lastAdScriptSignature = "";
let latestRemoteDailyTipsMap = null;
const alertedNotifIds = new Set();

// ---------------------- Session Preferences (Online-Only RTDB Architecture) ----------------------

function loadLocalSnapshot() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const saved = JSON.parse(raw);
    state.lang = saved.lang === "en" ? "en" : "bn";
    state.activeUserId = saved.activeUserId || "";
    if (Array.isArray(saved.cart)) state.cart = saved.cart;
    // Strip any legacy cached entities so stale demo/offline records are never restored
    saveLocalSnapshot();
  } catch (err) {
    console.warn("Local session restore skipped:", err);
  }
}

function saveLocalSnapshot() {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        lang: state.lang,
        activeUserId: state.activeUserId,
        cart: state.cart
      })
    );
  } catch (err) {
    console.warn("Local session save skipped:", err);
  }
}

function mergeById(baseList, incomingList) {
  const map = new Map();
  for (const item of baseList) {
    if (item && item.id) map.set(item.id, item);
  }
  for (const item of incomingList) {
    if (item && item.id) map.set(item.id, item);
  }
  return Array.from(map.values());
}

function upsertArrayItem(arr, item) {
  const idx = arr.findIndex((x) => x.id === item.id);
  if (idx >= 0) {
    arr[idx] = item;
  } else {
    arr.push(item);
  }
}

function normalizeAdConfig(raw) {
  const merged = { ...DEFAULT_AD_CONFIG, ...(raw && typeof raw === "object" ? raw : {}) };
  if (!String(merged.unityGameId || "").trim()) merged.unityGameId = DEFAULT_AD_CONFIG.unityGameId;
  if (!String(merged.androidBannerId || "").trim()) merged.androidBannerId = DEFAULT_AD_CONFIG.androidBannerId;
  if (!String(merged.androidInterstitialId || "").trim()) merged.androidInterstitialId = DEFAULT_AD_CONFIG.androidInterstitialId;
  if (!String(merged.androidRewardedId || "").trim()) merged.androidRewardedId = DEFAULT_AD_CONFIG.androidRewardedId;
  return merged;
}

function normalizeDailyTipsList(rawMap, managedFlag) {
  const hasObj = rawMap && typeof rawMap === "object" && Object.keys(rawMap).length > 0;
  if (!hasObj) {
    return managedFlag ? [] : [...DEFAULT_DAILY_TIPS];
  }
  return Object.entries(rawMap)
    .map(([k, v]) => ({
      id: v?.id || k,
      titleBn: String(v?.titleBn || "").trim(),
      contentBn: String(v?.contentBn || "").trim(),
      categoryBn: String(v?.categoryBn || "কৃষি পরামর্শ").trim(),
      emoji: String(v?.emoji || "🌾").trim() || "🌾",
      isActive: v?.isActive !== false,
      createdAt: Number(v?.createdAt || 0),
      updatedAt: Number(v?.updatedAt || v?.createdAt || 0)
    }))
    .filter((t) => t.titleBn && t.contentBn)
    .sort((a, b) => (b.updatedAt || b.createdAt || 0) - (a.updatedAt || a.createdAt || 0));
}

function normalizeHeroSlidersList(rawMap) {
  if (!rawMap || typeof rawMap !== "object") return [];
  return Object.entries(rawMap)
    .map(([k, v]) => ({
      id: v?.id || k,
      badgeBn: String(v?.badgeBn || "").trim(),
      titleBn: String(v?.titleBn || "").trim(),
      subtitleBn: String(v?.subtitleBn || "").trim(),
      bullet1Bn: String(v?.bullet1Bn || "").trim(),
      bullet2Bn: String(v?.bullet2Bn || "").trim(),
      bullet3Bn: String(v?.bullet3Bn || "").trim(),
      ctaTextBn: String(v?.ctaTextBn || "এখনই অর্ডার করুন").trim() || "এখনই অর্ডার করুন",
      targetRoute: String(v?.targetRoute || "browse").trim() || "browse",
      imageUri: String(v?.imageUri || "").trim(),
      sortOrder: Number(v?.sortOrder || 1),
      isActive: v?.isActive !== false,
      updatedAt: Number(v?.updatedAt || 0)
    }))
    .filter((s) => s.titleBn)
    .sort((a, b) => (a.sortOrder || 99) - (b.sortOrder || 99) || (b.updatedAt || 0) - (a.updatedAt || 0));
}

async function initFirebaseRealtime() {
  try {
    const [{ initializeApp, getApps }, dbMod] = await Promise.all([
      import("https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js"),
      import("https://www.gstatic.com/firebasejs/10.14.1/firebase-database.js")
    ]);
    const app = getApps().length ? getApps()[0] : initializeApp(RTDB_CONFIG);
    const database = dbMod.getDatabase(app, RTDB_CONFIG.databaseURL);
    rtdbRef = dbMod.ref(database);
    rtdbApi = dbMod;

    const { child, onValue, onChildAdded } = dbMod;

    onValue(child(rtdbRef, ".info/connected"), (snap) => {
      state.rtdbConnected = Boolean(snap.val());
      updateHeaderChrome();
    });

    function applyRemoteRoot(rootVal) {
      const root = rootVal && typeof rootVal === "object" ? rootVal : {};
      state.users = root.users && typeof root.users === "object"
        ? Object.entries(root.users).map(([k, v]) => ({ id: k, ...v }))
        : [];
      if (state.activeUserId && state.activeUserId !== SUPERADMIN_CREDENTIALS.userId && !state.users.some((u) => u.id === state.activeUserId)) {
        state.activeUserId = "";
        saveLocalSnapshot();
      }
      state.products = root.products && typeof root.products === "object"
        ? Object.entries(root.products).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      state.orders = root.orders && typeof root.orders === "object"
        ? Object.entries(root.orders).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      state.notifications = root.notifications && typeof root.notifications === "object"
        ? Object.entries(root.notifications).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      state.reviews = root.reviews && typeof root.reviews === "object"
        ? Object.entries(root.reviews).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      state.complaints = root.complaints && typeof root.complaints === "object"
        ? Object.entries(root.complaints).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      state.auditLogs = root.auditLogs && typeof root.auditLogs === "object"
        ? Object.entries(root.auditLogs).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      state.activationPayments = root.activationPayments && typeof root.activationPayments === "object"
        ? Object.entries(root.activationPayments).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.submittedAt || b.createdAt || 0) - (a.submittedAt || a.createdAt || 0))
        : [];
      state.farmerPaymentSettings = root.farmerPaymentSettings && typeof root.farmerPaymentSettings === "object"
        ? root.farmerPaymentSettings
        : {};
      state.orderPayments = root.orderPayments && typeof root.orderPayments === "object"
        ? Object.entries(root.orderPayments).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      state.adConfig = normalizeAdConfig(root.adConfig);
      latestRemoteDailyTipsMap = root.dailyTips && typeof root.dailyTips === "object" ? root.dailyTips : null;
      state.dailyTipsManaged = Boolean(root.dailyTipsMeta && root.dailyTipsMeta.managed) || Boolean(latestRemoteDailyTipsMap && Object.keys(latestRemoteDailyTipsMap).length > 0);
      state.dailyTips = normalizeDailyTipsList(latestRemoteDailyTipsMap, state.dailyTipsManaged);
      state.paymentSettings = root.paymentSettings && typeof root.paymentSettings === "object"
        ? { ...DEFAULT_PAYMENT_SETTINGS, ...root.paymentSettings }
        : { ...DEFAULT_PAYMENT_SETTINGS };
      state.heroSliders = normalizeHeroSlidersList(root.heroSliders);
      evaluateAccountExpiries(Date.now(), false);
      updateHeaderChrome();
      renderCurrentView();
    }

    try {
      const initialSnap = await dbMod.get(rtdbRef);
      state.rtdbConnected = true;
      applyRemoteRoot(initialSnap.val());
    } catch (initGetErr) {
      console.warn("Initial RTDB fetch skipped, relying on listeners:", initGetErr);
    }

    onValue(child(rtdbRef, "users"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        state.users = Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
        if (state.activeUserId && state.activeUserId !== SUPERADMIN_CREDENTIALS.userId && !state.users.some((u) => u.id === state.activeUserId)) {
          state.activeUserId = "";
          saveLocalSnapshot();
        }
        evaluateAccountExpiries(Date.now(), false);
        updateHeaderChrome();
        renderCurrentView();
      } else if (val === null) {
        state.users = [];
        if (state.activeUserId && state.activeUserId !== SUPERADMIN_CREDENTIALS.userId) {
          state.activeUserId = "";
          saveLocalSnapshot();
        }
        updateHeaderChrome();
        renderCurrentView();
      }
    });

    onValue(child(rtdbRef, "categories"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        const remoteCats = Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
        state.categories = mergeById(MASTER_CATEGORIES, remoteCats).sort((a, b) => (a.sortOrder || 99) - (b.sortOrder || 99));
        renderCurrentView();
      }
    });

    onValue(child(rtdbRef, "locations"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        const remoteLocs = Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
        state.locations = mergeById(buildInitialSeedLocations(), remoteLocs);
        renderCurrentView();
      }
    });

    onValue(child(rtdbRef, "products"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        state.products = Object.entries(val)
          .map(([k, v]) => ({ id: k, ...v }))
          .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
      } else if (val === null) {
        state.products = [];
      }
      renderCurrentView();
    });

    onValue(child(rtdbRef, "orders"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        state.orders = Object.entries(val)
          .map(([k, v]) => ({ id: k, ...v }))
          .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
      } else if (val === null) {
        state.orders = [];
      }
      renderCurrentView();
    });

    onValue(child(rtdbRef, "notifications"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        state.notifications = Object.entries(val)
          .map(([k, v]) => ({ id: k, ...v }))
          .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
        checkAndAlertIncomingNotifications();
      } else if (val === null) {
        state.notifications = [];
      }
      updateHeaderChrome();
      if (state.page === "notifications" || state.page === "farmer-dashboard") {
        renderCurrentView();
      }
    });

    onValue(child(rtdbRef, "reviews"), (snap) => {
      const val = snap.val();
      state.reviews = val && typeof val === "object"
        ? Object.entries(val).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
    });

    onValue(child(rtdbRef, "complaints"), (snap) => {
      const val = snap.val();
      state.complaints = val && typeof val === "object"
        ? Object.entries(val).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
    });

    onValue(child(rtdbRef, "auditLogs"), (snap) => {
      const val = snap.val();
      state.auditLogs = val && typeof val === "object"
        ? Object.entries(val).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
    });

    onValue(child(rtdbRef, "adConfig"), (snap) => {
      const val = snap.val();
      state.adConfig = normalizeAdConfig(val);
      renderCurrentView();
    });

    onValue(child(rtdbRef, "activationPayments"), (snap) => {
      const val = snap.val();
      state.activationPayments = val && typeof val === "object"
        ? Object.entries(val).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.submittedAt || b.createdAt || 0) - (a.submittedAt || a.createdAt || 0))
        : [];
      renderCurrentView();
    });

    onValue(child(rtdbRef, "paymentSettings"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        state.paymentSettings = { ...DEFAULT_PAYMENT_SETTINGS, ...val };
        renderCurrentView();
      } else if (val === null) {
        state.paymentSettings = { ...DEFAULT_PAYMENT_SETTINGS };
        renderCurrentView();
      }
    });

    onValue(child(rtdbRef, "farmerPaymentSettings"), (snap) => {
      const val = snap.val();
      state.farmerPaymentSettings = val && typeof val === "object" ? val : {};
      renderCurrentView();
    });

    onValue(child(rtdbRef, "orderPayments"), (snap) => {
      const val = snap.val();
      state.orderPayments = val && typeof val === "object"
        ? Object.entries(val).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      renderCurrentView();
    });

    onValue(child(rtdbRef, "dailyTipsMeta"), (snap) => {
      const meta = snap.val();
      state.dailyTipsManaged = Boolean(meta && meta.managed) || Boolean(latestRemoteDailyTipsMap && Object.keys(latestRemoteDailyTipsMap).length > 0);
      state.dailyTips = normalizeDailyTipsList(latestRemoteDailyTipsMap, state.dailyTipsManaged);
      renderCurrentView();
    });

    onValue(child(rtdbRef, "dailyTips"), (snap) => {
      const val = snap.val();
      latestRemoteDailyTipsMap = val && typeof val === "object" ? val : null;
      if (latestRemoteDailyTipsMap && Object.keys(latestRemoteDailyTipsMap).length > 0) {
        state.dailyTipsManaged = true;
      }
      state.dailyTips = normalizeDailyTipsList(latestRemoteDailyTipsMap, state.dailyTipsManaged);
      renderCurrentView();
    });

    onValue(child(rtdbRef, "heroSliders"), (snap) => {
      const val = snap.val();
      state.heroSliders = normalizeHeroSlidersList(val);
      renderCurrentView();
    });
  } catch (err) {
    console.warn("Firebase RTDB connection failed:", err);
    state.rtdbConnected = false;
    updateHeaderChrome();
  }
}

async function rtdbPush(path, data) {
  if (!rtdbRef || !rtdbApi) return false;
  try {
    await rtdbApi.set(rtdbApi.child(rtdbRef, path), data);
    return true;
  } catch (err) {
    console.warn("RTDB write failed (" + path + "):", err);
    return false;
  }
}

async function rtdbDelete(path) {
  if (!rtdbRef || !rtdbApi) return false;
  try {
    await rtdbApi.remove(rtdbApi.child(rtdbRef, path));
    return true;
  } catch (err) {
    console.warn("RTDB delete failed (" + path + "):", err);
    return false;
  }
}

async function rtdbFetchUserByPhoneStrict(phone) {
  const target = normalizePhone(phone);
  if (!target) return { status: "NOT_FOUND", user: null };
  if (!rtdbRef || !rtdbApi) return { status: "UNREACHABLE", user: null };
  try {
    const snap = await rtdbApi.get(rtdbApi.child(rtdbRef, "users"));
    if (!snap.exists()) return { status: "NOT_FOUND", user: null };
    const val = snap.val();
    if (!val || typeof val !== "object") return { status: "NOT_FOUND", user: null };
    const allUsers = Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
    state.users = allUsers;
    const match = allUsers.find((u) => normalizePhone(u.phone) === target || u.phone === phone);
    return match ? { status: "FOUND", user: match } : { status: "NOT_FOUND", user: null };
  } catch (err) {
    console.warn("RTDB strict user lookup failed:", err);
    return { status: "UNREACHABLE", user: null };
  }
}

async function rtdbFetchUserByPhone(phone) {
  const res = await rtdbFetchUserByPhoneStrict(phone);
  return res.status === "FOUND" ? res.user : null;
}

async function rtdbFetchAllActivationPayments() {
  if (!rtdbRef || !rtdbApi) return state.activationPayments;
  try {
    const snap = await rtdbApi.get(rtdbApi.child(rtdbRef, "activationPayments"));
    if (!snap.exists()) return [];
    const val = snap.val();
    if (!val || typeof val !== "object") return [];
    const list = Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
    state.activationPayments = list;
    return list;
  } catch (err) {
    console.warn("RTDB activationPayments lookup failed:", err);
    return state.activationPayments;
  }
}

async function rtdbFetchAllOrderPayments() {
  if (!rtdbRef || !rtdbApi) return state.orderPayments;
  try {
    const snap = await rtdbApi.get(rtdbApi.child(rtdbRef, "orderPayments"));
    if (!snap.exists()) return [];
    const val = snap.val();
    if (!val || typeof val !== "object") return [];
    const list = Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
    state.orderPayments = list;
    return list;
  } catch (err) {
    console.warn("RTDB orderPayments lookup failed:", err);
    return state.orderPayments;
  }
}

// ---------------------- Computed Helpers ----------------------

function normalizePhone(raw) {
  const digits = String(raw || "").trim().replace(/[^\d]/g, "");
  if (digits.startsWith("880") && digits.length >= 13) return "0" + digits.slice(3);
  return String(raw || "").trim();
}

function normalizeTxId(raw) {
  return String(raw || "").trim().replace(/\s+/g, "").toUpperCase();
}

function addCalendarYears(startMs, years = FIXED_VALIDITY_YEARS) {
  const d = new Date(Number(startMs || Date.now()));
  d.setFullYear(d.getFullYear() + Number(years || FIXED_VALIDITY_YEARS));
  return d.getTime();
}

function getActiveUser() {
  if (!state.activeUserId) return null;
  if (state.activeUserId === SUPERADMIN_CREDENTIALS.userId) {
    return (
      state.users.find((u) => u.id === SUPERADMIN_CREDENTIALS.userId) || {
        id: SUPERADMIN_CREDENTIALS.userId,
        fullName: "সুপার অ্যাডমিন",
        phone: SUPERADMIN_CREDENTIALS.id,
        role: "ADMIN",
        division: "ঢাকা",
        district: "ঢাকা",
        upazila: "সাভার",
        unionName: "কেন্দ্রীয়",
        village: "সারা বাংলাদেশ তদারকি",
        farmBio: "প্ল্যাটফর্ম তদারকি ও যাচাইকরণ অ্যাডমিন",
        nidMasked: "SUPERADMIN",
        verificationStatus: "VERIFIED",
        accountStatus: "ACTIVE",
        validityYears: FIXED_VALIDITY_YEARS,
        activationDate: Date.now(),
        expiryDate: 0,
        isMobileVerified: true,
        rating: 5.0,
        reviewCount: 0
      }
    );
  }
  return state.users.find((u) => u.id === state.activeUserId) || null;
}

function isCurrentUserAdsSuppressed() {
  const user = getActiveUser();
  return Boolean(user && user.adsDisabled === true);
}

function requireActiveMarketplaceUser(actionLabel = "এই কার্যক্রম") {
  const user = getActiveUser();
  if (!user) {
    showToast(`${actionLabel} করতে অনুগ্রহ করে লগইন বা নিবন্ধন করুন।`, "error");
    navigate("auth-login");
    return null;
  }
  if (user.role === "ADMIN") return user;
  if ((user.accountStatus || "PAYMENT_PENDING") === "ACTIVE" && user.expiryDate && user.expiryDate <= Date.now()) {
    evaluateAccountExpiries(Date.now(), true);
  }
  const freshUser = getActiveUser() || user;
  if (!isMarketplaceActive(freshUser)) {
    const st = freshUser.accountStatus || "PAYMENT_PENDING";
    const msg = st === "SUSPENDED"
      ? "আপনার অ্যাকাউন্ট স্থগিত (Suspended) রয়েছে। সহায়তার জন্য অ্যাডমিনের সাথে যোগাযোগ করুন।"
      : st === "EXPIRED"
      ? "আপনার ২ বছরের অ্যাকাউন্ট মেয়াদ শেষ হয়েছে। নবায়নের জন্য ৳৩০০ পেমেন্ট জমা দিন।"
      : st === "PAYMENT_UNDER_REVIEW"
      ? "আপনার ৳৩০০ অ্যাক্টিভেশন পেমেন্ট যাচাইাধীন রয়েছে। অনুমোদনের পর কার্যক্রম চালু হবে।"
      : "মার্কেটপ্লেস ব্যবহার করতে আপনার অ্যাকাউন্ট অ্যাক্টিভেট (৳৩০০ / ২ বছর) করা আবশ্যক।";
    showToast(msg, "error");
    navigate(st === "SUSPENDED" ? "profile" : "activation");
    return null;
  }
  return freshUser;
}

async function evaluateAccountExpiries(now = Date.now(), pushRemote = true) {
  const settings = state.paymentSettings || DEFAULT_PAYMENT_SETTINGS;
  let changed = false;

  const emitReminderOnce = async (userId, role, type, titleBn, messageBn) => {
    const dedupKey = `${userId}:${type}`;
    const already = state.notifications.some(
      (n) => n.userId === userId && n.type === type && Number(n.createdAt || 0) > now - 35 * 24 * 60 * 60 * 1000
    );
    if (already) return;
    const notif = {
      id: shortId("not"),
      userId,
      targetRole: role || "BUYER",
      senderRole: "SYSTEM",
      orderId: dedupKey,
      type,
      titleBn,
      messageBn,
      isRead: false,
      createdAt: now
    };
    state.notifications.unshift(notif);
    changed = true;
    if (pushRemote) await rtdbPush(`notifications/${notif.id}`, notif);
  };

  for (const u of state.users) {
    if (!u || u.role === "ADMIN") continue;
    const status = u.accountStatus || "PAYMENT_PENDING";
    const exp = Number(u.expiryDate || 0);
    if (status === "ACTIVE" && exp > 0) {
      const remainingMs = exp - now;
      const daysLeft = Math.ceil(remainingMs / (24 * 60 * 60 * 1000));
      if (remainingMs <= 0) {
        u.accountStatus = "EXPIRED";
        u.updatedAt = now;
        changed = true;
        if (pushRemote) await rtdbPush(`users/${u.id}`, u);
        if (settings.notifyOnExpiry !== false) {
          await emitReminderOnce(
            u.id,
            u.role,
            "ACCOUNT_EXPIRED",
            "অ্যাকাউন্টের মেয়াদ শেষ হয়েছে",
            "আপনার ২ বছরের অ্যাকাউন্ট মেয়াদ শেষ হয়েছে। পুনরায় সক্রিয় করতে ৳৩০০ নবায়ন ফি জমা দিন।"
          );
        }
      } else if (daysLeft <= 7 && settings.notify7Days !== false) {
        await emitReminderOnce(
          u.id,
          u.role,
          "EXPIRY_REMINDER_7D",
          "মেয়াদ শেষ হতে ৭ দিন বা তার কম বাকি",
          `আপনার অ্যাকাউন্টের মেয়াদ ${daysLeft} দিনের মধ্যে শেষ হবে (${formatDateBn(exp)})। সময়মতো নবায়ন করুন।`
        );
      } else if (daysLeft <= 30 && settings.notify30Days !== false) {
        await emitReminderOnce(
          u.id,
          u.role,
          "EXPIRY_REMINDER_30D",
          "অ্যাকাউন্ট নবায়নের স্মারক (৩০ দিন)",
          `আপনার অ্যাকাউন্টের মেয়াদ ${daysLeft} দিন পর (${formatDateBn(exp)}) শেষ হবে।`
        );
      }
    }
  }
  if (changed) saveLocalSnapshot();
}

function getVisibleNotifications() {
  const user = getActiveUser();
  if (user && user.role === "ADMIN") return state.notifications;
  return state.notifications.filter((n) => {
    if (!user) return n.userId === "ALL" && n.targetRole === "ALL";
    return (
      n.userId === user.id ||
      (n.userId === "ALL" && (!n.targetRole || n.targetRole === "ALL" || n.targetRole === user.role))
    );
  });
}

function checkAndAlertIncomingNotifications() {
  const user = getActiveUser();
  if (!user || user.role === "ADMIN") return;
  for (const n of getVisibleNotifications()) {
    if (n.isRead) continue;
    if ((n.createdAt || 0) < APP_LAUNCH_TIME - 30000) continue;
    if (alertedNotifIds.has(n.id)) continue;
    alertedNotifIds.add(n.id);
    showToast(`🔔 ${n.titleBn}: ${n.messageBn}`, n.type === "ORDER_NEW" ? "success" : "info");
    if (typeof Notification !== "undefined" && Notification.permission === "granted") {
      try {
        new Notification(n.titleBn, { body: n.messageBn, icon: `${ASSET_BASE}logo.png` });
      } catch (_) {}
    }
  }
}

function t(bn, en) {
  return state.lang === "en" ? en : bn;
}

// ---------------------- Toast & Modal ----------------------

function showToast(msg, variant = "info") {
  const container = document.getElementById("toast-container");
  if (!container) return;
  const el = document.createElement("div");
  const colors = {
    success: "bg-[#105B34] text-white border-emerald-400",
    error: "bg-red-700 text-white border-red-400",
    info: "bg-[#202820] text-white border-emerald-500"
  };
  el.className = `pointer-events-auto px-4 py-3 rounded-2xl shadow-xl border-l-4 text-sm font-medium transition-all duration-300 ${colors[variant] || colors.info}`;
  el.textContent = msg;
  container.appendChild(el);
  setTimeout(() => {
    el.style.opacity = "0";
    setTimeout(() => el.remove(), 300);
  }, 4200);
}

function openModal(htmlContent) {
  const modal = document.getElementById("modal-root");
  modal.innerHTML = `<div class="bg-white rounded-3xl shadow-2xl max-w-lg w-full max-h-[90vh] overflow-y-auto p-6 relative border border-emerald-100">${htmlContent}</div>`;
  modal.classList.remove("hidden");
}

function closeModal() {
  const modal = document.getElementById("modal-root");
  modal.classList.add("hidden");
  modal.innerHTML = "";
}

// ---------------------- Navigation & Header Chrome ----------------------

function updateHeaderChrome() {
  const user = getActiveUser();
  const role = user?.role || "GUEST";
  const isAdmin = role === "ADMIN";
  const isFarmer = role === "FARMER";
  const isBuyer = role === "BUYER";
  const unreadCount = getVisibleNotifications().filter((n) => !n.isRead).length;
  const farmerPendingOrders = isFarmer
    ? state.orders.filter((o) => o.farmerId === user.id && ["PENDING", "PAYMENT_PENDING"].includes(o.status)).length
    : 0;

  const brandLogoEl = document.getElementById("header-brand-logo");
  if (brandLogoEl) {
    if (user && user.profileImageUri) {
      brandLogoEl.src = user.profileImageUri;
      brandLogoEl.alt = user.fullName || "ব্যবহারকারীর প্রোফাইল ছবি";
      brandLogoEl.className = "w-10 h-10 rounded-full object-cover border-2 border-[#168447] shadow-sm";
    } else {
      brandLogoEl.src = `${ASSET_BASE}logo.png`;
      brandLogoEl.alt = "কৃষি বন্ধু লোগো";
      brandLogoEl.className = "w-10 h-10 rounded-xl object-cover border border-emerald-200 shadow-sm";
    }
  }

  const dot = document.getElementById("rtdb-status-dot");
  if (dot) {
    const live = state.isOnline && state.rtdbConnected;
    dot.className = `inline-block w-2 h-2 rounded-full ${live ? "bg-emerald-500" : state.isOnline ? "bg-amber-400" : "bg-red-500"}`;
    dot.title = live
      ? "অনলাইন ও রিয়েল-টাইম Firebase সিঙ্ক সচল"
      : state.isOnline
      ? "ইন্টারনেট সচল (Firebase রিয়েল-টাইম সংযোগ হচ্ছে...)"
      : "অফলাইন মোড";
  }

  const rolePill = document.getElementById("role-mode-pill");
  if (rolePill) {
    if (user) {
      rolePill.classList.remove("hidden");
      if (isFarmer) {
        rolePill.textContent = "👨‍🌾 কৃষক মোড";
        rolePill.className = "px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-[#105B34] border border-emerald-300";
      } else if (isBuyer) {
        rolePill.textContent = "🛍️ ক্রেতা মোড";
        rolePill.className = "px-2 py-0.5 rounded-full text-[10px] font-bold bg-sky-100 text-sky-900 border border-sky-200";
      } else {
        rolePill.textContent = "🛡️ সুপার অ্যাডমিন";
        rolePill.className = "px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-900 border border-amber-300";
      }
    } else {
      rolePill.classList.add("hidden");
      rolePill.textContent = "";
    }
  }

  const langBtn = document.getElementById("lang-btn");
  if (langBtn) langBtn.textContent = state.lang === "bn" ? "EN" : "বাংলা";

  const notifBadge = document.getElementById("notif-badge");
  if (notifBadge) {
    notifBadge.textContent = unreadCount > 99 ? "99+" : String(unreadCount);
    notifBadge.classList.toggle("hidden", unreadCount === 0);
  }

  const cartBadge = document.getElementById("cart-badge");
  if (cartBadge) {
    cartBadge.textContent = String(state.cart.length);
    cartBadge.classList.toggle("hidden", state.cart.length === 0);
  }

  const cartBtn = document.getElementById("nav-cart-btn");
  if (cartBtn) {
    cartBtn.classList.toggle("hidden", isFarmer && state.cart.length === 0);
  }

  const navItems = isFarmer
    ? [
        { key: "home", label: "আমার খামার", action: "window.kbApp.navigate('home')", active: state.page === "home" },
        {
          key: "farmer-products",
          label: "আমার ফসল",
          action: "window.kbApp.navigate('farmer-dashboard', { tab: 'products' })",
          active: state.page === "farmer-dashboard" && state.farmerTab === "products"
        },
        {
          key: "farmer-add",
          label: "+ ফসল যোগ",
          action: "window.kbApp.navigate('farmer-dashboard', { tab: 'add' })",
          active: state.page === "farmer-dashboard" && state.farmerTab === "add"
        },
        {
          key: "farmer-orders",
          label: farmerPendingOrders > 0 ? `প্রাপ্ত অর্ডার (${farmerPendingOrders})` : "প্রাপ্ত অর্ডার",
          action: "window.kbApp.navigate('farmer-dashboard', { tab: 'orders' })",
          active: (state.page === "farmer-dashboard" && state.farmerTab === "orders") || state.page === "orders"
        },
        { key: "browse", label: "বাজার দেখুন", action: "window.kbApp.navigate('browse')", active: state.page === "browse" },
        { key: "info", label: "তথ্য ও নীতি", action: "window.kbApp.navigate('info')", active: state.page === "info" }
      ]
    : isBuyer
    ? [
        { key: "home", label: "হোম", action: "window.kbApp.navigate('home')", active: state.page === "home" },
        { key: "browse", label: "ফসল খুঁজুন", action: "window.kbApp.navigate('browse')", active: state.page === "browse" },
        { key: "farmers", label: "কৃষক খুঁজুন", action: "window.kbApp.navigate('farmers')", active: state.page === "farmers" },
        { key: "orders", label: "আমার অর্ডার", action: "window.kbApp.navigate('orders')", active: state.page === "orders" },
        { key: "info", label: "তথ্য ও নীতি", action: "window.kbApp.navigate('info')", active: state.page === "info" }
      ]
    : isAdmin
    ? [
        { key: "home", label: "হোম", action: "window.kbApp.navigate('home')", active: state.page === "home" },
        { key: "browse", label: "ফসল খুঁজুন", action: "window.kbApp.navigate('browse')", active: state.page === "browse" },
        { key: "farmers", label: "কৃষক খুঁজুন", action: "window.kbApp.navigate('farmers')", active: state.page === "farmers" },
        { key: "orders", label: "অর্ডার", action: "window.kbApp.navigate('orders')", active: state.page === "orders" },
        { key: "info", label: "তথ্য ও নীতি", action: "window.kbApp.navigate('info')", active: state.page === "info" },
        { key: "admin", label: "অ্যাডমিন প্যানেল", action: "window.kbApp.navigate('admin')", active: state.page === "admin", isAdminBtn: true }
      ]
    : [
        { key: "home", label: "হোম", action: "window.kbApp.navigate('home')", active: state.page === "home" },
        { key: "browse", label: "ফসল খুঁজুন", action: "window.kbApp.navigate('browse')", active: state.page === "browse" },
        { key: "farmers", label: "কৃষক খুঁজুন", action: "window.kbApp.navigate('farmers')", active: state.page === "farmers" },
        { key: "sell", label: "বিক্রি করুন", action: "window.kbApp.goSell()", active: state.page === "farmer-dashboard" },
        { key: "orders", label: "অর্ডার", action: "window.kbApp.navigate('orders')", active: state.page === "orders" },
        { key: "info", label: "তথ্য ও নীতি", action: "window.kbApp.navigate('info')", active: state.page === "info" }
      ];

  const desktopNav = document.getElementById("desktop-nav");
  if (desktopNav) {
    desktopNav.innerHTML = navItems
      .map((item) => {
        const cls = item.isAdminBtn
          ? `nav-btn px-3 py-2 rounded-lg bg-amber-50 text-amber-800 border border-amber-200 hover:bg-amber-100 ${item.active ? "font-bold ring-2 ring-amber-300" : ""}`
          : `nav-btn px-3 py-2 rounded-lg hover:bg-[#EAF7EF] ${item.active ? "bg-[#EAF7EF] text-[#105B34] font-bold" : ""}`;
        return `<button onclick="${item.action}" class="${cls}">${escapeHtml(item.label)}</button>`;
      })
      .join("");
  }

  const mobileNav = document.getElementById("mobile-quick-nav");
  if (mobileNav) {
    mobileNav.innerHTML = navItems
      .map((item) => {
        const cls = item.isAdminBtn
          ? `px-2.5 py-1.5 rounded-lg whitespace-nowrap bg-amber-50 text-amber-800 border border-amber-200 ${item.active ? "font-bold" : ""}`
          : `px-2.5 py-1.5 rounded-lg whitespace-nowrap hover:bg-[#EAF7EF] ${item.active ? "bg-[#EAF7EF] text-[#105B34] font-bold" : ""}`;
        return `<button onclick="${item.action}" class="${cls}">${escapeHtml(item.label)}</button>`;
      })
      .join("");
  }

  const authSlot = document.getElementById("auth-header-slot");
  if (authSlot) {
    if (user) {
      const roleLabel = isAdmin ? "অ্যাডমিন" : isFarmer ? "কৃষক" : "ক্রেতা";
      const activeOk = isMarketplaceActive(user);
      const targetPage = isAdmin ? "admin" : "profile";
      authSlot.innerHTML = `
        <div class="flex items-center gap-1.5">
          ${!isAdmin && !activeOk ? `
            <button onclick="window.kbApp.navigate('activation')"
              class="px-2.5 py-1.5 rounded-xl bg-amber-100 text-amber-900 text-[11px] font-bold border border-amber-300 hover:bg-amber-200">
              ⚡ অ্যাক্টিভেশন
            </button>
          ` : ""}
          <button onclick="window.kbApp.navigate('${targetPage}')"
            class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold border border-emerald-200 hover:bg-emerald-100 max-w-[160px] truncate flex items-center gap-1.5">
            ${user.profileImageUri ? `<img src="${escapeHtml(user.profileImageUri)}" alt="" class="w-4 h-4 rounded-full object-cover" />` : ""}
            <span>${escapeHtml(user.fullName)} (${roleLabel})</span>
          </button>
          <button onclick="window.kbApp.logout()" class="px-2.5 py-1.5 rounded-xl text-xs font-semibold text-red-600 hover:bg-red-50 border border-red-200">
            ${t("লগআউট", "Logout")}
          </button>
        </div>
      `;
    } else {
      authSlot.innerHTML = `
        <div class="flex items-center gap-1.5">
          <button onclick="window.kbApp.navigate('auth-login')" class="px-3 py-1.5 rounded-xl border border-emerald-200 text-xs font-semibold text-[#105B34] hover:bg-[#EAF7EF]">
            ${t("লগইন", "Login")}
          </button>
          <button onclick="window.kbApp.navigate('auth-register-buyer')" class="hidden sm:inline-flex px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-semibold hover:bg-[#105B34]">
            ${t("নিবন্ধন", "Register")}
          </button>
        </div>
      `;
    }
  }
}

function navigate(page, params = {}) {
  state.page = page;
  state.pageParams = params;
  if (params.tab && page === "info") state.infoTab = params.tab;
  if (params.tab && page === "farmer-dashboard") state.farmerTab = params.tab;
  if (params.tab && page === "admin") state.adminTab = params.tab;
  if (params.mode && page === "activation") state.activationMode = params.mode;
  window.scrollTo({ top: 0, behavior: "smooth" });
  renderCurrentView();
}

// ---------------------- Business Operations ----------------------

async function login(identifier, rawPassword) {
  const cleanId = String(identifier || "").trim();
  if (!cleanId || !rawPassword) {
    showToast("আইডি/মোবাইল নম্বর এবং পাসওয়ার্ড দিন।", "error");
    return false;
  }

  if (cleanId.toLowerCase() === SUPERADMIN_CREDENTIALS.id) {
    if (rawPassword !== SUPERADMIN_CREDENTIALS.password) {
      showToast("সুপার অ্যাডমিন পাসওয়ার্ড সঠিক নয়।", "error");
      return false;
    }
    const adminHash = await hashPassword(SUPERADMIN_CREDENTIALS.password);
    const adminUser = {
      id: SUPERADMIN_CREDENTIALS.userId,
      fullName: "সুপার অ্যাডমিন",
      phone: SUPERADMIN_CREDENTIALS.id,
      passwordHash: adminHash,
      role: "ADMIN",
      division: "ঢাকা",
      district: "ঢাকা",
      upazila: "সাভার",
      unionName: "কেন্দ্রীয়",
      village: "সারা বাংলাদেশ তদারকি",
      farmBio: "প্ল্যাটফর্ম তদারকি ও যাচাইকরণ অ্যাডমিন",
      nidMasked: "SUPERADMIN",
      verificationStatus: "VERIFIED",
      accountStatus: "ACTIVE",
      validityYears: FIXED_VALIDITY_YEARS,
      activationDate: Date.now(),
      expiryDate: 0,
      isMobileVerified: true,
      rating: 5.0,
      reviewCount: 0,
      createdAt: Date.now()
    };
    upsertArrayItem(state.users, adminUser);
    state.activeUserId = adminUser.id;
    saveLocalSnapshot();
    showToast("স্বাগতম, সুপার অ্যাডমিন!", "success");
    navigate("admin");
    return true;
  }

  const normalizedPhone = normalizePhone(cleanId);
  const expectedHash = await hashPassword(rawPassword);
  const lookup = await rtdbFetchUserByPhoneStrict(normalizedPhone);
  if (lookup.status === "UNREACHABLE") {
    showToast("অনলাইন সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি। ইন্টারনেট সংযোগ চালু করে আবার চেষ্টা করুন।", "error");
    return false;
  }
  const user = lookup.status === "FOUND" ? lookup.user : null;
  if (!user) {
    showToast("এই মোবাইল নম্বরে কোনো অ্যাকাউন্ট পাওয়া যায়নি।", "error");
    return false;
  }
  if (user.passwordHash !== expectedHash) {
    showToast("পাসওয়ার্ড সঠিক নয়।", "error");
    return false;
  }
  state.activeUserId = user.id;
  await evaluateAccountExpiries(Date.now(), true);
  saveLocalSnapshot();
  const refreshed = getActiveUser() || user;
  showToast(`স্বাগতম, ${refreshed.fullName}!`, "success");
  navigate(
    !isMarketplaceActive(refreshed)
      ? ((refreshed.accountStatus || "PAYMENT_PENDING") === "SUSPENDED" ? "profile" : "activation")
      : "home"
  );
  return true;
}

async function resetPassword(phone, newPassword, confirmPassword) {
  const cleanPhone = normalizePhone(phone || "");
  const rawNew = String(newPassword || "");
  const rawConfirm = String(confirmPassword || "");
  if (cleanPhone.length < 10) {
    showToast("সঠিক নিবন্ধিত মোবাইল নম্বর দিন (যেমন: 017XXXXXXXX)।", "error");
    return false;
  }
  if (cleanPhone.toLowerCase() === SUPERADMIN_CREDENTIALS.id) {
    showToast("সুপার অ্যাডমিন পাসওয়ার্ড এখান থেকে পরিবর্তন করা যাবে না।", "error");
    return false;
  }
  if (rawNew.length < 6) {
    showToast("নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।", "error");
    return false;
  }
  if (rawNew !== rawConfirm) {
    showToast("নতুন পাসওয়ার্ড ও নিশ্চিত পাসওয়ার্ড মেলেনি।", "error");
    return false;
  }
  const lookup = await rtdbFetchUserByPhoneStrict(cleanPhone);
  if (lookup.status === "UNREACHABLE") {
    showToast("অনলাইন সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি। পরে আবার চেষ্টা করুন।", "error");
    return false;
  }
  if (lookup.status !== "FOUND" || !lookup.user) {
    showToast("এই মোবাইল নম্বরে কোনো নিবন্ধিত অ্যাকাউন্ট পাওয়া যায়নি।", "error");
    return false;
  }

  const user = lookup.user;
  const now = Date.now();
  user.passwordHash = await hashPassword(rawNew);
  user.updatedAt = now;
  upsertArrayItem(state.users, user);

  const audit = {
    id: shortId("aud"),
    actorId: user.id,
    actorRole: user.role,
    actionBn: "পাসওয়ার্ড রিসেট (ওয়েব)",
    targetEntity: "User",
    targetId: user.id,
    detailsBn: `${user.fullName} (${user.phone}) নতুন পাসওয়ার্ড সেট করেছেন`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`users/${user.id}`, user);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast("পাসওয়ার্ড সফলভাবে পরিবর্তিত হয়েছে! এখন নতুন পাসওয়ার্ড দিয়ে লগইন করুন।", "success");
  closeModal();
  renderCurrentView();
  return true;
}

async function registerUser(form) {
  const fullName = String(form.fullName || "").trim();
  const phone = normalizePhone(form.phone || "");
  const rawPassword = String(form.password || "");
  const confirmPassword = String(form.confirmPassword ?? rawPassword);
  const role = form.role === "FARMER" ? "FARMER" : "BUYER";

  if (!fullName || phone.length < 10 || rawPassword.length < 6) {
    showToast("নাম, সঠিক মোবাইল নম্বর ও কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড দিন।", "error");
    return false;
  }
  if (rawPassword !== confirmPassword) {
    showToast("পাসওয়ার্ড ও নিশ্চিত পাসওয়ার্ড মেলেনি।", "error");
    return false;
  }
  if (phone.toLowerCase() === SUPERADMIN_CREDENTIALS.id) {
    showToast("এই আইডি ব্যবহার করা যাবে না।", "error");
    return false;
  }
  const regLookup = await rtdbFetchUserByPhoneStrict(phone);
  if (regLookup.status === "UNREACHABLE") {
    showToast("অনলাইন সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি। ইন্টারনেট সংযোগ চালু করে আবার চেষ্টা করুন।", "error");
    return false;
  }
  if (regLookup.status === "FOUND") {
    showToast("এই মোবাইল নম্বরটি ইতোমধ্যে নিবন্ধিত। লগইন করুন।", "error");
    return false;
  }

  const resolvedDistrict = form.district || "ঢাকা";
  const districtObj = BD_DISTRICTS.find((d) => d.districtBn === resolvedDistrict);
  const resolvedDivision = form.division || districtObj?.divisionBn || "ঢাকা";
  const resolvedUpazila = form.upazila || districtObj?.upazilas?.[0] || resolvedDistrict;

  const now = Date.now();
  const uid = shortId(role.toLowerCase());
  const passwordHash = await hashPassword(rawPassword);
  const nidLast4 = String(form.nidLastFour || "").trim();
  const user = {
    id: uid,
    fullName,
    phone,
    passwordHash,
    role,
    division: resolvedDivision,
    district: resolvedDistrict,
    upazila: resolvedUpazila,
    unionName: String(form.unionName || "").trim(),
    village: String(form.village || "").trim(),
    farmBio: String(form.farmBio || "").trim(),
    profileImageUri: "",
    nidMasked: nidLast4 ? `NID-****-${nidLast4.slice(-4)}` : "",
    verificationStatus: "PENDING",
    accountStatus: "PAYMENT_PENDING",
    validityYears: FIXED_VALIDITY_YEARS,
    activationDate: 0,
    expiryDate: 0,
    lastPaymentId: "",
    isMobileVerified: true,
    rating: 5.0,
    reviewCount: 0,
    createdAt: now,
    updatedAt: now
  };

  upsertArrayItem(state.users, user);
  state.activeUserId = uid;
  const audit = {
    id: shortId("aud"),
    actorId: uid,
    actorRole: role,
    actionBn: "নতুন অ্যাকাউন্ট নিবন্ধন (ওয়েব)",
    targetEntity: "User",
    targetId: uid,
    detailsBn: `${user.fullName} — ${user.upazila}, ${user.district}, ${user.division} (পেমেন্ট অপেক্ষমাণ)`,
    createdAt: now
  };
  upsertArrayItem(state.auditLogs, audit);
  saveLocalSnapshot();

  await rtdbPush(`users/${uid}`, user);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast(`নিবন্ধন সম্পন্ন! এখন ৳${FIXED_ACTIVATION_FEE_BDT} অ্যাক্টিভেশন ফি জমা দিয়ে ২ বছরের জন্য অ্যাকাউন্ট সক্রিয় করুন।`, "success");
  navigate("activation");
  return true;
}

async function updateUserProfile(form) {
  const user = getActiveUser();
  if (!user) {
    showToast("প্রোফাইল সম্পাদনা করতে লগইন করুন।", "error");
    navigate("auth-login");
    return false;
  }
  const fullName = String(form.fullName || "").trim();
  if (fullName.length < 2) {
    showToast("সঠিক পূর্ণ নাম লিখুন।", "error");
    return false;
  }
  const resolvedDistrict = form.district || user.district || "ঢাকা";
  const districtObj = BD_DISTRICTS.find((d) => d.districtBn === resolvedDistrict);
  const resolvedDivision = form.division || districtObj?.divisionBn || user.division || "ঢাকা";
  const resolvedUpazila = form.upazila || districtObj?.upazilas?.[0] || user.upazila || resolvedDistrict;
  const nidLast4 = String(form.nidLastFour || "").trim();
  const now = Date.now();

  user.fullName = fullName;
  user.division = resolvedDivision;
  user.district = resolvedDistrict;
  user.upazila = resolvedUpazila;
  user.unionName = String(form.unionName ?? user.unionName ?? "").trim();
  user.village = String(form.village ?? user.village ?? "").trim();
  user.farmBio = String(form.farmBio ?? user.farmBio ?? "").trim();
  if (form.profileImageUri !== undefined) {
    user.profileImageUri = String(form.profileImageUri || "").trim();
  }
  if (nidLast4) {
    user.nidMasked = `NID-****-${nidLast4.slice(-4)}`;
  }
  user.updatedAt = now;

  if (user.role === "FARMER") {
    for (const prod of state.products) {
      if (prod.farmerId === user.id) {
        prod.farmerName = user.fullName;
        prod.division = user.division;
        prod.district = user.district;
        prod.upazila = user.upazila;
        prod.unionName = user.unionName;
        prod.village = user.village;
        prod.updatedAt = now;
        await rtdbPush(`products/${prod.id}`, prod);
      }
    }
  }

  const audit = {
    id: shortId("aud"),
    actorId: user.id,
    actorRole: user.role,
    actionBn: "প্রোফাইল হালনাগাদ (ওয়েব)",
    targetEntity: "User",
    targetId: user.id,
    detailsBn: `${user.fullName} — ${user.upazila}, ${user.district}`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();
  await rtdbPush(`users/${user.id}`, user);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast("প্রোফাইল সফলভাবে হালনাগাদ হয়েছে!", "success");
  renderCurrentView();
  return true;
}

async function submitActivationPayment({ paymentMethod, senderMobile, transactionId, amount, receiptUri, isRenewal = false, isAdRemoval = false }) {
  const user = getActiveUser();
  if (!user || user.role === "ADMIN") {
    showToast("পেমেন্ট জমা দিতে কৃষক বা ক্রেতা হিসেবে লগইন করুন।", "error");
    return false;
  }
  if (!isAdRemoval && user.accountStatus === "SUSPENDED") {
    showToast("আপনার অ্যাকাউন্ট স্থগিত (Suspended) রয়েছে।", "error");
    return false;
  }
  if (isAdRemoval && user.adsDisabled === true) {
    showToast("আপনার অ্যাকাউন্টে ইতোমধ্যেই সব বিজ্ঞাপন বন্ধ (Ad-Free) রয়েছে।", "info");
    return false;
  }
  const hasPending = state.activationPayments.some(
    (p) => p.userId === user.id && p.status === "PENDING" && ((p.paymentType === "AD_FREE") === Boolean(isAdRemoval))
  );
  if (hasPending) {
    showToast("আপনার একটি পেমেন্ট ইতোমধ্যে অ্যাডমিন যাচাইয়ের জন্য অপেক্ষমাণ রয়েছে।", "error");
    return false;
  }

  const cleanSender = normalizePhone(senderMobile);
  const cleanTxId = normalizeTxId(transactionId);
  const cleanReceipt = String(receiptUri || "").trim();
  const adRemovalFee = Number(state.paymentSettings?.adRemovalFee || DEFAULT_PAYMENT_SETTINGS.adRemovalFee || 200);
  const expectedAmount = isAdRemoval ? adRemovalFee : FIXED_ACTIVATION_FEE_BDT;
  const feeAmount = Number(amount || expectedAmount);

  if (cleanSender.length < 10) {
    showToast("সঠিক প্রেরক মোবাইল নম্বর (যেমন: 017XXXXXXXX) দিন।", "error");
    return false;
  }
  if (cleanTxId.length < 4) {
    showToast("সঠিক ট্রানজেকশন আইডি (Transaction ID) দিন।", "error");
    return false;
  }
  if (isAdRemoval ? feeAmount < adRemovalFee : feeAmount !== FIXED_ACTIVATION_FEE_BDT) {
    showToast(isAdRemoval ? `বিজ্ঞাপন মুক্ত করার ফি নির্ধারিত ৳${adRemovalFee}।` : `অ্যাক্টিভেশন ও নবায়ন ফি নির্ধারিত ৳${FIXED_ACTIVATION_FEE_BDT}।`, "error");
    return false;
  }

  const remotePayments = await rtdbFetchAllActivationPayments();
  const combinedPayments = mergeById(state.activationPayments, remotePayments);
  state.activationPayments = combinedPayments;
  const dupTx = combinedPayments.some((p) => normalizeTxId(p.transactionId) === cleanTxId);
  if (dupTx) {
    showToast("এই ট্রানজেকশন আইডি (TxID) ইতোমধ্যে ব্যবহৃত হয়েছে! প্রতিটি পেমেন্টের জন্য নতুন TxID দিন।", "error");
    return false;
  }

  const now = Date.now();
  const settings = state.paymentSettings || DEFAULT_PAYMENT_SETTINGS;
  const chosenMethod = paymentMethod === "NAGAD" ? "NAGAD" : "BKASH";
  const paymentType = isAdRemoval ? "AD_FREE" : (isRenewal || user.activationDate > 0 ? "RENEWAL" : "ACTIVATION");
  const payment = {
    id: shortId("actpay"),
    userId: user.id,
    userName: user.fullName,
    userPhone: user.phone,
    userRole: user.role,
    accountRole: user.role,
    accountType: user.role,
    paymentType,
    paymentMethod: chosenMethod,
    receiverNumber: chosenMethod === "NAGAD" ? settings.nagadNumber : settings.bKashNumber,
    receiverAccountType: chosenMethod === "NAGAD" ? settings.nagadAccountType : settings.bKashAccountType,
    senderMobile: cleanSender,
    transactionId: cleanTxId,
    amount: expectedAmount,
    validityYears: FIXED_VALIDITY_YEARS,
    receiptUri: cleanReceipt,
    status: "PENDING",
    isRenewal: !isAdRemoval && Boolean(isRenewal || user.activationDate > 0),
    adminNotes: "",
    submittedAt: now,
    createdAt: now,
    updatedAt: now,
    reviewedAt: 0,
    reviewedBy: ""
  };

  state.activationPayments.unshift(payment);
  if (!isAdRemoval) {
    if (user.accountStatus !== "ACTIVE") {
      user.accountStatus = "PAYMENT_UNDER_REVIEW";
    }
    user.lastPaymentId = payment.id;
    user.updatedAt = now;
  }

  const adminNotif = {
    id: shortId("not"),
    userId: "ALL",
    targetRole: "ADMIN",
    senderRole: user.role,
    orderId: payment.id,
    type: isAdRemoval ? "AD_FREE_PAYMENT_SUBMITTED" : "ACTIVATION_PAYMENT_SUBMITTED",
    titleBn: isAdRemoval
      ? `বিজ্ঞাপন বন্ধ (Ad-Free) পেমেন্ট (৳${expectedAmount})`
      : `নতুন অ্যাকাউন্ট অ্যাক্টিভেশন পেমেন্ট (৳${FIXED_ACTIVATION_FEE_BDT})`,
    messageBn: `${user.fullName} (${user.role === "FARMER" ? "কৃষক" : "ক্রেতা"}) • ${payment.paymentMethod} TxID: ${cleanTxId}`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: user.id,
    actorRole: user.role,
    actionBn: isAdRemoval
      ? "বিজ্ঞাপন মুক্ত (Ad-Free) পেমেন্ট জমা (ওয়েব)"
      : payment.isRenewal ? "অ্যাকাউন্ট নবায়ন পেমেন্ট জমা (ওয়েব)" : "অ্যাকাউন্ট অ্যাক্টিভেশন পেমেন্ট জমা (ওয়েব)",
    targetEntity: "ActivationPayment",
    targetId: payment.id,
    detailsBn: `${user.fullName} — ৳${expectedAmount} | TxID: ${cleanTxId}`,
    createdAt: now
  };
  state.notifications.unshift(adminNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`activationPayments/${payment.id}`, payment);
  if (!isAdRemoval) await rtdbPush(`users/${user.id}`, user);
  await rtdbPush(`notifications/${adminNotif.id}`, adminNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast(
    isAdRemoval
      ? "বিজ্ঞাপন মুক্ত করার পেমেন্ট জমা হয়েছে! অ্যাডমিন অনুমোদনের পর আপনার সব বিজ্ঞাপন বন্ধ হবে।"
      : "পেমেন্ট তথ্য জমা হয়েছে! অ্যাডমিন যাচাইয়ের পর ২ বছরের জন্য অ্যাকাউন্ট সক্রিয় হবে।",
    "success"
  );
  renderCurrentView();
  return true;
}

async function adminApproveActivationPayment(paymentId, notes = "") {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র সুপার অ্যাডমিন পেমেন্ট অনুমোদন করতে পারেন।", "error");
    return false;
  }
  const payment = state.activationPayments.find((p) => p.id === paymentId);
  if (!payment) return false;
  const user = state.users.find((u) => u.id === payment.userId);
  if (!user) {
    showToast("সংশ্লিষ্ট ব্যবহারকারী পাওয়া যায়নি।", "error");
    return false;
  }
  const now = Date.now();
  const isAdRemoval = payment.paymentType === "AD_FREE";

  payment.status = "APPROVED";
  payment.reviewedAt = now;
  payment.reviewedBy = admin.id;
  payment.updatedAt = now;
  payment.adminNotes = String(notes || "").trim();

  if (isAdRemoval) {
    user.adsDisabled = true;
    user.updatedAt = now;

    const notif = {
      id: shortId("not"),
      userId: user.id,
      targetRole: user.role,
      senderRole: "ADMIN",
      orderId: payment.id,
      type: "AD_FREE_APPROVED",
      titleBn: "সব বিজ্ঞাপন বন্ধ (Ad-Free) হয়েছে",
      messageBn: `অভিনন্দন! আপনার ৳${payment.amount || 200} পেমেন্ট (TxID: ${payment.transactionId}) অনুমোদিত হয়েছে। এখন থেকে অ্যাপ ও ওয়েবসাইটে কোনো বিজ্ঞাপন দেখাবে না।`,
      isRead: false,
      createdAt: now
    };
    const audit = {
      id: shortId("aud"),
      actorId: admin.id,
      actorRole: "ADMIN",
      actionBn: "বিজ্ঞাপন বন্ধ (Ad-Free) অনুমোদন",
      targetEntity: "ActivationPayment",
      targetId: payment.id,
      detailsBn: `${user.fullName} — TxID: ${payment.transactionId} -> adsDisabled=true`,
      createdAt: now
    };
    state.notifications.unshift(notif);
    state.auditLogs.unshift(audit);
    saveLocalSnapshot();

    await rtdbPush(`activationPayments/${payment.id}`, payment);
    await rtdbPush(`users/${user.id}`, user);
    await rtdbPush(`notifications/${notif.id}`, notif);
    await rtdbPush(`auditLogs/${audit.id}`, audit);

    showToast(`${user.fullName}-এর জন্য সব বিজ্ঞাপন বন্ধ (Ad-Free) করা হয়েছে!`, "success");
    closeModal();
    renderCurrentView();
    return true;
  }

  const baseDate = user.accountStatus === "ACTIVE" && Number(user.expiryDate || 0) > now ? Number(user.expiryDate) : now;
  const nextExpiry = addCalendarYears(baseDate, FIXED_VALIDITY_YEARS);

  user.accountStatus = "ACTIVE";
  user.verificationStatus = "VERIFIED";
  user.validityYears = FIXED_VALIDITY_YEARS;
  user.activationDate = user.activationDate && user.activationDate > 0 ? user.activationDate : now;
  user.expiryDate = nextExpiry;
  user.lastPaymentId = payment.id;
  user.updatedAt = now;

  if (user.role === "FARMER") {
    for (const prod of state.products) {
      if (prod.farmerId === user.id) {
        prod.farmerVerified = true;
        await rtdbPush(`products/${prod.id}`, prod);
      }
    }
  }

  const notif = {
    id: shortId("not"),
    userId: user.id,
    targetRole: user.role,
    senderRole: "ADMIN",
    orderId: payment.id,
    type: "ACTIVATION_APPROVED",
    titleBn: "অ্যাকাউন্ট সক্রিয় হয়েছে (২ বছর মেয়াদ)",
    messageBn: `অভিনন্দন! আপনার ৳${FIXED_ACTIVATION_FEE_BDT} পেমেন্ট (TxID: ${payment.transactionId}) অনুমোদিত হয়েছে। মেয়াদ: ${formatDateBn(nextExpiry)} পর্যন্ত।`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: "অ্যাকাউন্ট অ্যাক্টিভেশন অনুমোদন",
    targetEntity: "ActivationPayment",
    targetId: payment.id,
    detailsBn: `${user.fullName} — TxID: ${payment.transactionId} -> ACTIVE (${formatDateBn(nextExpiry)})`,
    createdAt: now
  };
  state.notifications.unshift(notif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`activationPayments/${payment.id}`, payment);
  await rtdbPush(`users/${user.id}`, user);
  await rtdbPush(`notifications/${notif.id}`, notif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast(`${user.fullName}-এর অ্যাকাউন্ট ২ বছরের জন্য সক্রিয় হয়েছে!`, "success");
  closeModal();
  renderCurrentView();
  return true;
}

async function adminRejectActivationPayment(paymentId, reason, requestResubmission = false) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") return false;
  const payment = state.activationPayments.find((p) => p.id === paymentId);
  if (!payment) return false;
  const user = state.users.find((u) => u.id === payment.userId);
  const now = Date.now();
  const isAdRemoval = payment.paymentType === "AD_FREE";
  const cleanReason = String(reason || "").trim() || (requestResubmission ? "সঠিক TxID ও স্পষ্ট রসিদ পুনরায় জমা দিন।" : "পেমেন্ট তথ্য যাচাইয়ে অমিল পাওয়া গেছে।");

  payment.status = requestResubmission ? "RESUBMISSION_REQUESTED" : "REJECTED";
  payment.reviewedAt = now;
  payment.reviewedBy = admin.id;
  payment.updatedAt = now;
  payment.adminNotes = cleanReason;

  if (user) {
    if (!isAdRemoval) {
      const stillValidActive = user.accountStatus === "ACTIVE" && Number(user.expiryDate || 0) > now;
      if (!stillValidActive) {
        user.accountStatus = requestResubmission ? "PAYMENT_PENDING" : "PAYMENT_REJECTED";
      }
      user.updatedAt = now;
      await rtdbPush(`users/${user.id}`, user);
    }

    const notif = {
      id: shortId("not"),
      userId: user.id,
      targetRole: user.role,
      senderRole: "ADMIN",
      orderId: payment.id,
      type: isAdRemoval ? "AD_FREE_REJECTED" : "ACTIVATION_REJECTED",
      titleBn: isAdRemoval
        ? (requestResubmission ? "Ad-Free পেমেন্ট পুনঃজমা প্রয়োজন" : "Ad-Free পেমেন্ট প্রত্যাখ্যাত")
        : (requestResubmission ? "পেমেন্ট পুনঃজমা প্রয়োজন" : "অ্যাক্টিভেশন পেমেন্ট প্রত্যাখ্যাত"),
      messageBn: `কারণ: ${cleanReason} (TxID: ${payment.transactionId})`,
      isRead: false,
      createdAt: now
    };
    state.notifications.unshift(notif);
    await rtdbPush(`notifications/${notif.id}`, notif);
  }

  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: requestResubmission ? "পেমেন্ট পুনঃজমার অনুরোধ" : "অ্যাক্টিভেশন পেমেন্ট প্রত্যাখ্যান",
    targetEntity: "ActivationPayment",
    targetId: payment.id,
    detailsBn: `${payment.userName} — ${cleanReason}`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`activationPayments/${payment.id}`, payment);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast(requestResubmission ? "পুনঃজমার অনুরোধ পাঠানো হয়েছে।" : "পেমেন্ট প্রত্যাখ্যাত হয়েছে।", "info");
  closeModal();
  renderCurrentView();
  return true;
}

async function adminSetAccountStatus(userId, newStatus, reason = "") {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") return false;
  const user = state.users.find((u) => u.id === userId);
  if (!user || user.role === "ADMIN") return false;

  const now = Date.now();
  user.accountStatus = newStatus;
  if (newStatus === "ACTIVE") {
    user.verificationStatus = "VERIFIED";
    user.activationDate = user.activationDate && user.activationDate > 0 ? user.activationDate : now;
    if (!user.expiryDate || Number(user.expiryDate) <= now) {
      user.expiryDate = addCalendarYears(now, FIXED_VALIDITY_YEARS);
    }
    user.validityYears = FIXED_VALIDITY_YEARS;
  }
  user.updatedAt = now;

  if (user.role === "FARMER") {
    const isVerified = newStatus === "ACTIVE" && user.verificationStatus === "VERIFIED";
    for (const prod of state.products) {
      if (prod.farmerId === user.id) {
        prod.farmerVerified = isVerified;
        await rtdbPush(`products/${prod.id}`, prod);
      }
    }
  }

  const notif = {
    id: shortId("not"),
    userId: user.id,
    targetRole: user.role,
    senderRole: "ADMIN",
    orderId: "",
    type: "ACCOUNT_STATUS_CHANGED",
    titleBn: `অ্যাকাউন্ট স্ট্যাটাস: ${ACCOUNT_STATUS_META[newStatus]?.labelBn || newStatus}`,
    messageBn: reason ? String(reason).trim() : `অ্যাডমিন আপনার অ্যাকাউন্টের অবস্থা ${ACCOUNT_STATUS_META[newStatus]?.labelBn || newStatus} হিসেবে সেট করেছেন।`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: `অ্যাকাউন্ট স্ট্যাটাস পরিবর্তন (${newStatus})`,
    targetEntity: "User",
    targetId: user.id,
    detailsBn: `${user.fullName} -> ${newStatus} ${reason ? `(${reason})` : ""}`,
    createdAt: now
  };
  state.notifications.unshift(notif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`users/${user.id}`, user);
  await rtdbPush(`notifications/${notif.id}`, notif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast(`${user.fullName} — ${ACCOUNT_STATUS_META[newStatus]?.labelBn || newStatus}`, "success");
  renderCurrentView();
  return true;
}

async function adminSetUserAdsDisabled(userId, disabled) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") return false;
  const user = state.users.find((u) => u.id === userId);
  if (!user || user.role === "ADMIN") return false;
  const now = Date.now();
  const nextVal = Boolean(disabled);
  user.adsDisabled = nextVal;
  user.updatedAt = now;

  const notif = {
    id: shortId("not"),
    userId: user.id,
    targetRole: user.role,
    senderRole: "ADMIN",
    orderId: "",
    type: nextVal ? "ADS_DISABLED_BY_ADMIN" : "ADS_ENABLED_BY_ADMIN",
    titleBn: nextVal ? "আপনার বিজ্ঞাপন বন্ধ করা হয়েছে" : "আপনার বিজ্ঞাপন পুনরায় চালু হয়েছে",
    messageBn: nextVal
      ? "অ্যাডমিন আপনার অ্যাকাউন্টের জন্য সব বিজ্ঞাপন বন্ধ করেছেন। এখন আপনি বিজ্ঞাপনমুক্ত অভিজ্ঞতা পাবেন।"
      : "অ্যাডমিন আপনার অ্যাকাউন্টের বিজ্ঞাপন প্রদর্শন পুনরায় চালু করেছেন।",
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: nextVal ? "ব্যবহারকারীর বিজ্ঞাপন বন্ধ (ওয়েব)" : "ব্যবহারকারীর বিজ্ঞাপন চালু (ওয়েব)",
    targetEntity: "User",
    targetId: user.id,
    detailsBn: `${user.fullName} -> adsDisabled=${nextVal}`,
    createdAt: now
  };
  state.notifications.unshift(notif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`users/${user.id}`, user);
  await rtdbPush(`notifications/${notif.id}`, notif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast(nextVal ? `${user.fullName}-এর বিজ্ঞাপন বন্ধ করা হয়েছে` : `${user.fullName}-এর বিজ্ঞাপন চালু করা হয়েছে`, "success");
  renderCurrentView();
  return true;
}

async function adminSaveHeroSlider(sliderDraft) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র সুপার অ্যাডমিন হিরো স্লাইডার পরিচালনা করতে পারেন।", "error");
    return false;
  }
  const titleBn = String(sliderDraft?.titleBn || "").trim();
  if (!titleBn) {
    showToast("স্লাইডারের মূল শিরোনাম আবশ্যক।", "error");
    return false;
  }
  const now = Date.now();
  const id = String(sliderDraft.id || "").trim() || shortId("sld");
  const existing = state.heroSliders.find((s) => s.id === id);
  const item = {
    id,
    badgeBn: String(sliderDraft.badgeBn || "১০০% দালালমুক্ত বাজার").trim(),
    titleBn,
    subtitleBn: String(sliderDraft.subtitleBn || "").trim(),
    bullet1Bn: String(sliderDraft.bullet1Bn || "কৃষকের ন্যায্য দাম").trim(),
    bullet2Bn: String(sliderDraft.bullet2Bn || "ক্রেতার ন্যায্য দামে ক্রয়").trim(),
    bullet3Bn: String(sliderDraft.bullet3Bn || "সরাসরি যোগাযোগ").trim(),
    ctaTextBn: String(sliderDraft.ctaTextBn || "এখনই অর্ডার করুন").trim() || "এখনই অর্ডার করুন",
    targetRoute: String(sliderDraft.targetRoute || "browse").trim() || "browse",
    imageUri: String(sliderDraft.imageUri || "").trim(),
    sortOrder: Math.max(1, Number(sliderDraft.sortOrder || existing?.sortOrder || state.heroSliders.length + 1)),
    isActive: sliderDraft.isActive !== false,
    updatedAt: now
  };
  state.heroSliders = [item, ...state.heroSliders.filter((s) => s.id !== id)]
    .sort((a, b) => (a.sortOrder || 99) - (b.sortOrder || 99) || (b.updatedAt || 0) - (a.updatedAt || 0));

  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: existing ? "হিরো স্লাইডার সম্পাদনা (ওয়েব)" : "নতুন হিরো স্লাইডার যোগ (ওয়েব)",
    targetEntity: "HeroSlider",
    targetId: id,
    detailsBn: `${item.titleBn} (ক্রম: ${item.sortOrder}, ${item.isActive ? "সক্রিয়" : "বন্ধ"})`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);

  await rtdbPush(`heroSliders/${id}`, item);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast("হিরো স্লাইডার সংরক্ষণ ও লাইভ হয়েছে!", "success");
  renderCurrentView();
  return true;
}

async function adminDeleteHeroSlider(sliderId) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") return false;
  const target = state.heroSliders.find((s) => s.id === sliderId);
  if (!target) return false;
  const now = Date.now();
  state.heroSliders = state.heroSliders.filter((s) => s.id !== sliderId);
  state.heroSlideIdx = 0;

  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: "হিরো স্লাইডার মুছে ফেলা (ওয়েব)",
    targetEntity: "HeroSlider",
    targetId: sliderId,
    detailsBn: `${target.titleBn} মুছে ফেলা হয়েছে`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);

  await rtdbDelete(`heroSliders/${sliderId}`);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast("হিরো স্লাইডার মুছে ফেলা হয়েছে।", "info");
  renderCurrentView();
  return true;
}

async function adminSavePaymentSettings(nextSettings) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র সুপার অ্যাডমিন পেমেন্ট সেটিংস পরিবর্তন করতে পারেন।", "error");
    return false;
  }
  const now = Date.now();
  const adRemovalFee = Number(nextSettings.adRemovalFee || DEFAULT_PAYMENT_SETTINGS.adRemovalFee || 200);
  const merged = {
    ...DEFAULT_PAYMENT_SETTINGS,
    ...nextSettings,
    farmerFee: FIXED_ACTIVATION_FEE_BDT,
    buyerFee: FIXED_ACTIVATION_FEE_BDT,
    renewalFee: FIXED_ACTIVATION_FEE_BDT,
    adRemovalFee: adRemovalFee > 0 ? adRemovalFee : 200,
    validityYears: FIXED_VALIDITY_YEARS,
    updatedAt: now
  };
  state.paymentSettings = merged;
  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: "পেমেন্ট সেটিংস হালনাগাদ (ওয়েব)",
    targetEntity: "PaymentSettings",
    targetId: "default",
    detailsBn: `bKash: ${merged.bKashNumber} (${merged.bKashAccountType}) | Nagad: ${merged.nagadNumber} | Ad-Free Fee: ৳${merged.adRemovalFee}`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();
  await rtdbPush("paymentSettings", merged);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast("পেমেন্ট সেটিংস সংরক্ষিত ও লাইভ হয়েছে!", "success");
  renderCurrentView();
  return true;
}

function logout() {
  state.activeUserId = "";
  saveLocalSnapshot();
  showToast("সফলভাবে লগআউট সম্পন্ন হয়েছে।", "info");
  navigate("home");
}

function addToCart(productId, qty) {
  const buyer = requireActiveMarketplaceUser("কার্টে ফসল যোগ");
  if (!buyer) return;
  if (buyer.role === "FARMER") {
    showToast("কৃষক অ্যাকাউন্ট থেকে কেনাকাটার কার্ট ব্যবহার করা যাবে না।", "error");
    return;
  }
  const product = state.products.find((p) => p.id === productId);
  const reqQty = Number(qty || 1);
  if (!product || reqQty <= 0) {
    showToast("সঠিক পরিমাণ নির্বাচন করুন।", "error");
    return;
  }
  if (!isAllowedUnit(product.unit)) {
    showToast("শুধুমাত্র মণ (১ মণ = ৪০ কেজি) এককে অর্ডার করা যাবে।", "error");
    return;
  }
  if (product.farmerId === buyer.id) {
    showToast("নিজের ফসল নিজে কেনা যাবে না।", "error");
    return;
  }
  if (product.status === "PENDING_APPROVAL" || product.status === "REJECTED") {
    showToast("এই ফসলটি এখনো অ্যাডমিন কর্তৃক অনুমোদিত নয়।", "error");
    return;
  }
  if (product.status !== "ACTIVE" || product.availableQuantity <= 0) {
    showToast("দুঃখিত, এই ফসলটির মজুত শেষ।", "error");
    return;
  }
  const existing = state.cart.find((c) => c.productId === productId);
  const newTotal = (existing ? existing.quantity : 0) + reqQty;
  if (newTotal > product.availableQuantity) {
    showToast(`সর্বোচ্চ মজুত ${formatQty(product.availableQuantity)} মণ পর্যন্ত যোগ করা যাবে।`, "error");
    return;
  }
  if (existing) {
    existing.quantity = newTotal;
    existing.maxAvailableQuantity = product.availableQuantity;
  } else {
    state.cart.push({
      id: shortId("cart"),
      productId: product.id,
      farmerId: product.farmerId,
      farmerName: product.farmerName,
      productNameBn: product.nameBn,
      unit: "মণ",
      pricePerUnit: product.pricePerUnit,
      quantity: reqQty,
      maxAvailableQuantity: product.availableQuantity,
      supportsFarmPickup: product.supportsFarmPickup !== false,
      supportsDirectDelivery: product.supportsDirectDelivery !== false,
      deliveryChargeEstimate: Number(product.deliveryChargeEstimate || 0)
    });
  }
  saveLocalSnapshot();
  updateHeaderChrome();
  showToast(`${product.nameBn} কার্টে যোগ করা হয়েছে!`, "success");
}

function updateCartQty(cartId, newQty) {
  const item = state.cart.find((c) => c.id === cartId);
  if (!item) return;
  const q = Number(newQty);
  if (q <= 0) {
    state.cart = state.cart.filter((c) => c.id !== cartId);
  } else {
    const liveProd = state.products.find((p) => p.id === item.productId);
    const maxAvail = liveProd ? liveProd.availableQuantity : item.maxAvailableQuantity;
    if (q > maxAvail) {
      showToast(`পর্যাপ্ত মজুত নেই (সর্বোচ্চ ${formatQty(maxAvail)} মণ)`, "error");
      return;
    }
    item.quantity = q;
  }
  saveLocalSnapshot();
  renderCurrentView();
}

function getFarmerPaymentSettings(farmerId) {
  const s = (state.farmerPaymentSettings && state.farmerPaymentSettings[farmerId]) || {};
  return { ...DEFAULT_FARMER_PAYMENT_SETTINGS, ...s, farmerId };
}

function hasAnyFarmerPaymentChannel(fps) {
  return Boolean(
    String(fps?.bKashNumber || "").trim() ||
    String(fps?.nagadNumber || "").trim() ||
    String(fps?.rocketNumber || "").trim() ||
    String(fps?.bankAccountInfo || "").trim()
  );
}

async function saveFarmerPaymentSettings({ bKashNumber, nagadNumber, rocketNumber, bankAccountInfo }) {
  const farmer = getActiveUser();
  if (!farmer || farmer.role !== "FARMER") {
    showToast("শুধুমাত্র কৃষক নিজস্ব পেমেন্ট নম্বর সেটিংস সংরক্ষণ করতে পারেন।", "error");
    return false;
  }
  const cleanBkash = String(bKashNumber || "").trim() ? normalizePhone(bKashNumber) : "";
  const cleanNagad = String(nagadNumber || "").trim() ? normalizePhone(nagadNumber) : "";
  const cleanRocket = String(rocketNumber || "").trim() ? normalizePhone(rocketNumber) : "";
  const cleanBank = String(bankAccountInfo || "").trim();

  if (cleanBkash && cleanBkash.length < 10) {
    showToast("সঠিক বিকাশ (bKash) মোবাইল নম্বর লিখুন।", "error");
    return false;
  }
  if (cleanNagad && cleanNagad.length < 10) {
    showToast("সঠিক নগদ (Nagad) মোবাইল নম্বর লিখুন।", "error");
    return false;
  }
  if (cleanRocket && cleanRocket.length < 10) {
    showToast("সঠিক রকেট (Rocket) মোবাইল নম্বর লিখুন।", "error");
    return false;
  }
  if (!cleanBkash && !cleanNagad && !cleanRocket && !cleanBank) {
    showToast("অন্তত একটি পেমেন্ট মাধ্যম (bKash / Nagad / Rocket / Bank) লিখুন।", "error");
    return false;
  }

  const now = Date.now();
  const payload = {
    farmerId: farmer.id,
    bKashNumber: cleanBkash,
    nagadNumber: cleanNagad,
    rocketNumber: cleanRocket,
    bankAccountInfo: cleanBank,
    updatedAt: now
  };
  state.farmerPaymentSettings = {
    ...(state.farmerPaymentSettings || {}),
    [farmer.id]: payload
  };

  const audit = {
    id: shortId("aud"),
    actorId: farmer.id,
    actorRole: farmer.role,
    actionBn: "কৃষকের সরাসরি পেমেন্ট নম্বর আপডেট (ওয়েব)",
    targetEntity: "FarmerPaymentSettings",
    targetId: farmer.id,
    detailsBn: `bKash: ${cleanBkash || "-"}, Nagad: ${cleanNagad || "-"}, Rocket: ${cleanRocket || "-"}`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`farmerPaymentSettings/${farmer.id}`, payload);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast("আপনার সরাসরি পেমেন্ট নম্বর সংরক্ষিত ও লাইভ হয়েছে!", "success");
  renderCurrentView();
  return true;
}

async function isDuplicateTransactionIdAnywhere(txNormalized) {
  if (!txNormalized) return false;
  const [remoteOrdersPay, remoteActPay] = await Promise.all([
    rtdbFetchAllOrderPayments(),
    rtdbFetchAllActivationPayments()
  ]);
  const allOp = mergeById(state.orderPayments, remoteOrdersPay);
  const allAct = mergeById(state.activationPayments, remoteActPay);
  state.orderPayments = allOp;
  state.activationPayments = allAct;
  const dupOp = allOp.some((p) => normalizeTxId(p.transactionIdNormalized || p.transactionId) === txNormalized);
  const dupAct = allAct.some((p) => normalizeTxId(p.transactionId) === txNormalized);
  return dupOp || dupAct;
}

async function placeOrdersFromCart({
  deliveryOption,
  deliveryAddress,
  paymentMethod,
  transactionRef,
  senderNumber,
  referenceNote,
  advanceAmount
}) {
  const buyer = requireActiveMarketplaceUser("পাইকারি অর্ডার নিশ্চিত");
  if (!buyer) return;
  if (!state.cart.length) {
    showToast("আপনার কার্ট খালি।", "error");
    return;
  }
  if (deliveryOption === "DIRECT_DELIVERY" && !String(deliveryAddress || "").trim()) {
    showToast("ডেলিভারির ঠিকানা লিখুন।", "error");
    return;
  }

  for (const cartItem of state.cart) {
    const prod = state.products.find((p) => p.id === cartItem.productId);
    if (!prod || prod.status !== "ACTIVE" || prod.availableQuantity < cartItem.quantity) {
      showToast(`${cartItem.productNameBn}-এর পর্যাপ্ত মজুত নেই।`, "error");
      return;
    }
  }

  const grouped = new Map();
  for (const item of state.cart) {
    if (!grouped.has(item.farmerId)) grouped.set(item.farmerId, []);
    grouped.get(item.farmerId).push(item);
  }

  // Compute total wholesale checkout value and validate advance payment amount (0 .. total)
  const cartTotalGrand = Array.from(grouped.entries()).reduce((sum, [, items]) => {
    const prodTotal = items.reduce((s, ci) => s + Number(ci.pricePerUnit || 0) * Number(ci.quantity || 0), 0);
    const maxDel = items.reduce((m, ci) => Math.max(m, Number(ci.deliveryChargeEstimate || 0)), 0);
    return sum + prodTotal + (deliveryOption === "FARM_PICKUP" ? 0 : maxDel);
  }, 0);

  const requestedAdvanceTotal = Math.round(Number(advanceAmount ?? 0) * 100) / 100;
  if (Number.isNaN(requestedAdvanceTotal) || requestedAdvanceTotal < 0 || requestedAdvanceTotal > cartTotalGrand + 0.01) {
    showToast(`অগ্রিম পেমেন্টের পরিমাণ ০ থেকে মোট মূল্যের (${formatBdt(cartTotalGrand)}) মধ্যে হতে হবে।`, "error");
    return;
  }

  const cleanTx = normalizeTxId(transactionRef || "");
  const cleanSender = normalizePhone(senderNumber || "") || buyer.phone;
  const cleanNote = String(referenceNote || "").trim();

  if (requestedAdvanceTotal > 0) {
    if (cleanTx.length < 4) {
      showToast("অগ্রিম পেমেন্টের জন্য সঠিক Transaction ID দিন।", "error");
      return;
    }
    if (["BKASH", "NAGAD", "ROCKET", "BANK_TRANSFER"].includes(paymentMethod)) {
      for (const [fId, fItems] of grouped.entries()) {
        const fps = getFarmerPaymentSettings(fId);
        const rec =
          paymentMethod === "BKASH"
            ? fps.bKashNumber
            : paymentMethod === "NAGAD"
            ? fps.nagadNumber
            : paymentMethod === "ROCKET"
            ? fps.rocketNumber
            : fps.bankAccountInfo;
        if (!rec) {
          showToast(`কৃষক ${fItems[0].farmerName} এখনো ${paymentMethod} নম্বর সেট করেননি। অন্য মাধ্যম বা ৳০ অগ্রিম বেছে নিন।`, "error");
          return;
        }
      }
    }
    if (await isDuplicateTransactionIdAnywhere(cleanTx)) {
      showToast(`এই Transaction ID (${cleanTx}) পূর্বেই ব্যবহৃত হয়েছে! নতুন ও সঠিক TxID দিন।`, "error");
      return;
    }
  }

  const now = Date.now();
  const createdOrders = [];
  let txUsed = false;

  for (const [farmerId, farmerItems] of grouped.entries()) {
    const farmer = state.users.find((u) => u.id === farmerId);
    const farmerPay = getFarmerPaymentSettings(farmerId);
    const orderId = shortId("ord");
    const orderCode = `KB-${Math.floor(1000 + Math.random() * 9000)}-${Math.floor(10 + Math.random() * 89)}`;
    let productTotal = 0;
    let maxDeliveryFee = 0;
    const itemsMap = {};

    for (const ci of farmerItems) {
      const prod = state.products.find((p) => p.id === ci.productId);
      const nextStock = Math.max(0, Number(prod.availableQuantity) - Number(ci.quantity));
      prod.availableQuantity = nextStock;
      prod.status = nextStock <= 0 ? "OUT_OF_STOCK" : "ACTIVE";
      prod.updatedAt = now;
      await rtdbPush(`products/${prod.id}`, prod);

      const lineTotal = ci.pricePerUnit * ci.quantity;
      productTotal += lineTotal;
      if ((ci.deliveryChargeEstimate || 0) > maxDeliveryFee) maxDeliveryFee = ci.deliveryChargeEstimate;
      const oiId = shortId("oi");
      itemsMap[oiId] = {
        id: oiId,
        orderId,
        productId: ci.productId,
        productNameBn: ci.productNameBn,
        unit: "মণ",
        quantity: ci.quantity,
        pricePerUnit: ci.pricePerUnit,
        lineTotal
      };
    }

    const deliveryCharge = deliveryOption === "FARM_PICKUP" ? 0 : maxDeliveryFee;
    const grandTotal = productTotal + deliveryCharge;
    const orderAdvance = grouped.size === 1
      ? Math.min(grandTotal, requestedAdvanceTotal)
      : cartTotalGrand > 0
      ? Math.min(grandTotal, Math.round((requestedAdvanceTotal * (grandTotal / cartTotalGrand)) * 100) / 100)
      : 0;
    const receiverNumber =
      paymentMethod === "BKASH"
        ? farmerPay.bKashNumber
        : paymentMethod === "NAGAD"
        ? farmerPay.nagadNumber
        : paymentMethod === "ROCKET"
        ? farmerPay.rocketNumber
        : paymentMethod === "BANK_TRANSFER"
        ? farmerPay.bankAccountInfo
        : "";
    const orderTxId = orderAdvance > 0
      ? (!txUsed ? (txUsed = true, cleanTx) : `${cleanTx}_${farmerId.slice(-4)}`)
      : "";
    const itemsSummary = farmerItems.map((i) => `${i.productNameBn} × ${formatQty(i.quantity)} মণ (${formatQty(i.quantity * MON_TO_KG)} কেজি)`).join(", ");

    const order = {
      id: orderId,
      orderCode,
      buyerId: buyer.id,
      buyerName: buyer.fullName,
      buyerPhone: buyer.phone,
      farmerId,
      farmerName: farmer?.fullName || farmerItems[0].farmerName,
      farmerPhone: farmer?.phone || "",
      status: "PAYMENT_PENDING",
      deliveryOption,
      deliveryAddress: deliveryOption === "FARM_PICKUP" ? "সরাসরি কৃষকের খামার থেকে সংগ্রহ" : deliveryAddress.trim(),
      division: farmer?.division || buyer.division,
      district: farmer?.district || buyer.district,
      upazila: farmer?.upazila || buyer.upazila,
      productTotal,
      deliveryCharge,
      grandTotal,
      advanceAmountRequested: orderAdvance,
      verifiedAdvanceTotal: 0,
      remainingDue: grandTotal,
      paymentMethod: paymentMethod || "BKASH",
      paymentSenderNumber: orderAdvance > 0 ? cleanSender : "",
      transactionReference: orderTxId,
      paymentReceiptUri: "",
      paymentStatus: orderAdvance > 0 ? "PENDING_VERIFICATION" : "ZERO_ADVANCE_PENDING",
      farmerPaymentConfirmed: false,
      farmerPaymentConfirmedAt: 0,
      adminPaymentVerified: false,
      adminPaymentVerifiedBy: "",
      adminPaymentVerifiedAt: 0,
      itemsSummary,
      createdAt: now,
      updatedAt: now,
      items: itemsMap
    };

    if (orderAdvance > 0) {
      const op = {
        id: shortId("op"),
        orderId,
        orderCode,
        buyerId: buyer.id,
        buyerName: buyer.fullName,
        farmerId,
        farmerName: order.farmerName,
        amount: orderAdvance,
        paymentMethod: order.paymentMethod,
        receiverNumber,
        senderNumber: cleanSender,
        transactionId: orderTxId,
        transactionIdNormalized: normalizeTxId(orderTxId),
        referenceNote: cleanNote,
        status: "PENDING_VERIFICATION",
        rejectionReason: "",
        verifiedBy: "",
        verifiedAt: 0,
        createdAt: now,
        updatedAt: now
      };
      state.orderPayments.unshift(op);
      await rtdbPush(`orderPayments/${op.id}`, op);
    }

    const farmerNotif = {
      id: shortId("not"),
      userId: farmerId,
      targetRole: "FARMER",
      senderRole: "BUYER",
      orderId,
      type: orderAdvance > 0 ? "ORDER_ADVANCE_SUBMITTED" : "ORDER_NEW",
      titleBn: orderAdvance > 0 ? `নতুন পাইকারি অর্ডার ও অগ্রিম (${orderCode})` : `নতুন পাইকারি অর্ডার (${orderCode})`,
      messageBn: orderAdvance > 0
        ? `ক্রেতা ${buyer.fullName} ${itemsSummary} (মোট ${formatBdt(grandTotal)}) অর্ডার করে ${formatBdt(orderAdvance)} অগ্রিম (${order.paymentMethod}, TxID: ${orderTxId}) জমা দিয়েছেন। নিজ অ্যাকাউন্ট ব্যালেন্স দেখে যাচাই করুন।`
        : `ক্রেতা ${buyer.fullName} ${itemsSummary} (মোট ${formatBdt(grandTotal)}) অর্ডার করেছেন (অগ্রিম ৳০)। অনুমোদন বা প্রত্যাখ্যান করুন।`,
      isRead: false,
      createdAt: now
    };
    const audit = {
      id: shortId("aud"),
      actorId: buyer.id,
      actorRole: buyer.role,
      actionBn: "পাইকারি অর্ডার ও অগ্রিম তথ্য তৈরি (ওয়েব)",
      targetEntity: "Order",
      targetId: orderId,
      detailsBn: `${orderCode} — মোট ${formatBdt(grandTotal)}, প্রস্তাবিত অগ্রিম ${formatBdt(orderAdvance)}, অবশিষ্ট বকেয়া ${formatBdt(grandTotal)}`,
      createdAt: now
    };

    state.orders.unshift(order);
    state.notifications.unshift(farmerNotif);
    state.auditLogs.unshift(audit);
    createdOrders.push(order);

    await rtdbPush(`orders/${orderId}`, order);
    await rtdbPush(`notifications/${farmerNotif.id}`, farmerNotif);
    await rtdbPush(`auditLogs/${audit.id}`, audit);
  }

  state.cart = [];
  saveLocalSnapshot();
  showToast(`${createdOrders.length}টি পাইকারি অর্ডার জমা হয়েছে! কৃষক আপনার সরাসরি পেমেন্ট যাচাই করলে অর্ডার Confirmed হবে।`, "success");
  navigate("orders");
}

async function createFarmerProduct(form) {
  const farmer = requireActiveMarketplaceUser("ফসল যোগ");
  if (!farmer) return false;
  if (farmer.role !== "FARMER") {
    showToast("শুধুমাত্র সক্রিয় কৃষক ফসল যুক্ত করতে পারেন।", "error");
    return false;
  }
  if (!isAllowedUnit(form.unit || "মণ")) {
    showToast("ফসল শুধুমাত্র মণ (১ মণ = ৪০ কেজি) এককে বিক্রি করা যাবে — কেজি অনুমোদিত নয়।", "error");
    return false;
  }
  const qty = Number(form.quantity);
  const price = Number(form.pricePerUnit);
  const fee = Number(form.deliveryCharge || 0);
  if (!String(form.nameBn || "").trim() || qty <= 0 || price <= 0 || fee < 0) {
    showToast("ফসলের নাম, সঠিক মণ পরিমাণ ও প্রতি মণের পাইকারি দাম দিন।", "error");
    return false;
  }
  const category = state.categories.find((c) => c.id === form.categoryId) || state.categories[0];
  const now = Date.now();
  const product = {
    id: shortId("prod"),
    farmerId: farmer.id,
    farmerName: farmer.fullName,
    farmerVerified: farmer.verificationStatus === "VERIFIED",
    categoryId: category.id,
    categoryNameBn: category.nameBn,
    nameBn: String(form.nameBn).trim(),
    nameEn: String(form.nameBn).trim(),
    descriptionBn: String(form.descriptionBn || "").trim(),
    imageUri: String(form.imageUri || "").trim(),
    availableQuantity: qty,
    unit: "মণ",
    pricePerUnit: price,
    harvestDate: String(form.harvestDate || "").trim() || "আজই সংগ্রহ",
    availabilityDate: String(form.availabilityDate || "").trim() || "আজই পাওয়া যাবে",
    division: farmer.division,
    district: farmer.district,
    upazila: farmer.upazila,
    unionName: farmer.unionName || "",
    village: farmer.village || "",
    pickupAddress: String(form.pickupAddress || "").trim() || `${farmer.upazila}, ${farmer.district}`,
    supportsFarmPickup: Boolean(form.supportsFarmPickup),
    supportsDirectDelivery: Boolean(form.supportsDirectDelivery),
    deliveryChargeEstimate: form.supportsDirectDelivery ? fee : 0,
    isWholesale: true,
    isPopular: false,
    status: "PENDING_APPROVAL",
    createdAt: now,
    updatedAt: now
  };

  state.products.unshift(product);
  const adminNotif = {
    id: shortId("not"),
    userId: SUPERADMIN_CREDENTIALS.userId,
    targetRole: "ADMIN",
    senderRole: "FARMER",
    orderId: "",
    type: "PRODUCT_APPROVAL_PENDING",
    titleBn: `নতুন ফসল যাচাইয়ের আবেদন: ${product.nameBn}`,
    messageBn: `কৃষক ${farmer.fullName} (${farmer.district}) নতুন ফসল "${product.nameBn}" (${formatQty(qty)} মণ, ${formatBdt(price)}/মণ) যোগ করেছেন। অ্যাডমিন অনুমোদনের পর প্রকাশিত হবে।`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: farmer.id,
    actorRole: farmer.role,
    actionBn: "নতুন পাইকারি ফসল জমা (যাচাই অপেক্ষমাণ)",
    targetEntity: "Product",
    targetId: product.id,
    detailsBn: `${product.nameBn} (${product.district}) — ${formatBdt(product.pricePerUnit)}/মণ`,
    createdAt: now
  };
  state.notifications.unshift(adminNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`products/${product.id}`, product);
  await rtdbPush(`notifications/${adminNotif.id}`, adminNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast("আপনার ফসল জমা হয়েছে! অ্যাডমিন যাচাই ও অনুমোদনের পর সবার জন্য প্রকাশিত হবে ✓", "success");
  state.farmerTab = "products";
  renderCurrentView();
  return true;
}

async function updateFarmerPriceAndStock(productId, newPrice, newStock) {
  const farmer = requireActiveMarketplaceUser("দাম ও মজুত সম্পাদনা");
  if (!farmer) return;
  const prod = state.products.find((p) => p.id === productId);
  if (!prod || prod.farmerId !== farmer.id) {
    showToast("অন্য কৃষকের পণ্যের দাম পরিবর্তন অনুমোদিত নয়।", "error");
    return;
  }
  const p = Number(newPrice);
  const s = Number(newStock);
  if (p <= 0 || s < 0) {
    showToast("সঠিক দাম ও মজুত দিন।", "error");
    return;
  }
  prod.pricePerUnit = p;
  prod.availableQuantity = s;
  prod.unit = "মণ";
  prod.isWholesale = true;
  if (!["PENDING_APPROVAL", "REJECTED"].includes(prod.status)) {
    prod.status = s > 0 ? "ACTIVE" : "OUT_OF_STOCK";
  }
  prod.updatedAt = Date.now();
  saveLocalSnapshot();
  await rtdbPush(`products/${prod.id}`, prod);
  showToast(
    prod.status === "PENDING_APPROVAL"
      ? "দাম ও মজুত সংরক্ষিত হয়েছে (অ্যাডমিন অনুমোদনের পর প্রকাশিত হবে)।"
      : "দাম ও মজুত আপডেট হয়েছে!",
    "success"
  );
  renderCurrentView();
}

async function submitOrderAdvancePayment(orderId, { amount, paymentMethod, senderNumber, transactionId, referenceNote }) {
  const buyer = requireActiveMarketplaceUser("অগ্রিম/আংশিক পেমেন্ট জমা");
  if (!buyer) return false;
  const order = state.orders.find((o) => o.id === orderId);
  if (!order || order.buyerId !== buyer.id) {
    showToast("শুধুমাত্র অর্ডারকারী ক্রেতা পেমেন্ট তথ্য জমা দিতে পারবেন।", "error");
    return false;
  }
  if (["REJECTED", "CANCELLED", "COMPLETED"].includes(order.status)) {
    showToast("এই অবস্থায় অর্ডারে পেমেন্ট জমা দেওয়া যাবে না।", "error");
    return false;
  }

  const verifiedTotal = Number(order.verifiedAdvanceTotal || 0);
  const currentRemainingDue = Math.max(0, Number(order.grandTotal || 0) - verifiedTotal);
  if (currentRemainingDue <= 0) {
    showToast("এই অর্ডারের সম্পূর্ণ মূল্য ইতোমধ্যে পরিশোধিত।", "info");
    return false;
  }

  const payAmount = Math.round(Number(amount || 0) * 100) / 100;
  if (Number.isNaN(payAmount) || payAmount <= 0 || payAmount > currentRemainingDue + 0.01) {
    showToast(`পেমেন্টের পরিমাণ অবশ্যই ৳১ থেকে অবশিষ্ট বকেয়া (${formatBdt(currentRemainingDue)}) এর মধ্যে হতে হবে।`, "error");
    return false;
  }

  const cleanSender = normalizePhone(senderNumber) || buyer.phone;
  const cleanTx = normalizeTxId(transactionId);
  if (cleanTx.length < 4) {
    showToast("সঠিক Transaction ID দিন।", "error");
    return false;
  }
  if (await isDuplicateTransactionIdAnywhere(cleanTx)) {
    showToast(`এই Transaction ID (${cleanTx}) পূর্বেই ব্যবহৃত হয়েছে!`, "error");
    return false;
  }

  const farmerPay = getFarmerPaymentSettings(order.farmerId);
  const receiverNumber =
    paymentMethod === "BKASH"
      ? farmerPay.bKashNumber
      : paymentMethod === "NAGAD"
      ? farmerPay.nagadNumber
      : paymentMethod === "ROCKET"
      ? farmerPay.rocketNumber
      : paymentMethod === "BANK_TRANSFER"
      ? farmerPay.bankAccountInfo
      : "";

  const now = Date.now();
  const payment = {
    id: shortId("op"),
    orderId: order.id,
    orderCode: order.orderCode,
    buyerId: buyer.id,
    buyerName: buyer.fullName,
    farmerId: order.farmerId,
    farmerName: order.farmerName,
    amount: Math.min(currentRemainingDue, payAmount),
    paymentMethod: paymentMethod || "BKASH",
    receiverNumber,
    senderNumber: cleanSender,
    transactionId: cleanTx,
    transactionIdNormalized: cleanTx,
    referenceNote: String(referenceNote || "").trim(),
    status: "PENDING_VERIFICATION",
    rejectionReason: "",
    verifiedBy: "",
    verifiedAt: 0,
    createdAt: now,
    updatedAt: now
  };
  state.orderPayments.unshift(payment);

  order.paymentMethod = payment.paymentMethod;
  order.paymentSenderNumber = cleanSender;
  order.transactionReference = cleanTx;
  order.paymentStatus = "PENDING_VERIFICATION";
  order.updatedAt = now;

  const farmerNotif = {
    id: shortId("not"),
    userId: order.farmerId,
    targetRole: "FARMER",
    senderRole: "BUYER",
    orderId: order.id,
    type: "ORDER_PAYMENT_SUBMITTED",
    titleBn: `নতুন অগ্রিম/আংশিক পেমেন্ট (${order.orderCode})`,
    messageBn: `ক্রেতা ${buyer.fullName} ${formatBdt(payment.amount)} (${payment.paymentMethod}, TxID: ${cleanTx}) পাঠিয়েছেন। নিজ অ্যাকাউন্ট ব্যালেন্স দেখে নিশ্চিত করুন।`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: buyer.id,
    actorRole: buyer.role,
    actionBn: "অর্ডার অগ্রিম/আংশিক পেমেন্ট জমা (ওয়েব)",
    targetEntity: "OrderPayment",
    targetId: payment.id,
    detailsBn: `${order.orderCode} — ${formatBdt(payment.amount)} (${payment.paymentMethod}, TxID: ${cleanTx})`,
    createdAt: now
  };
  state.notifications.unshift(farmerNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`orderPayments/${payment.id}`, payment);
  await rtdbPush(`orders/${order.id}`, order);
  await rtdbPush(`notifications/${farmerNotif.id}`, farmerNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast("পেমেন্ট তথ্য জমা হয়েছে! কৃষক যাচাই করে নিশ্চিত করলে অগ্রিম ব্যালেন্সে যোগ হবে।", "success");
  closeModal();
  renderCurrentView();
  return true;
}

async function farmerVerifyOrderPayment(paymentId, approved, rejectionReason = "") {
  const farmer = requireActiveMarketplaceUser("পেমেন্ট যাচাই ও নিশ্চিতকরণ");
  if (!farmer) return false;
  const payment = state.orderPayments.find((p) => p.id === paymentId);
  if (!payment) {
    showToast("পেমেন্ট রেকর্ড পাওয়া যায়নি।", "error");
    return false;
  }
  if (payment.farmerId !== farmer.id) {
    showToast("শুধুমাত্র সংশ্লিষ্ট কৃষক এই পেমেন্ট যাচাই করতে পারেন।", "error");
    return false;
  }
  if (payment.status !== "PENDING_VERIFICATION") {
    showToast("এই পেমেন্টটি ইতোমধ্যে যাচাই করা হয়েছে।", "info");
    return false;
  }
  const order = state.orders.find((o) => o.id === payment.orderId);
  if (!order) return false;

  const now = Date.now();
  payment.status = approved ? "CONFIRMED" : "REJECTED";
  payment.rejectionReason = approved ? "" : (String(rejectionReason || "").trim() || "কৃষক অ্যাকাউন্টে টাকা পাওয়া যায়নি বা তথ্য মেলেনি।");
  payment.verifiedBy = farmer.id;
  payment.verifiedAt = now;
  payment.updatedAt = now;

  const confirmedSumRaw = state.orderPayments
    .filter((p) => p.orderId === order.id && p.status === "CONFIRMED")
    .reduce((sum, p) => sum + Number(p.amount || 0), 0);
  const confirmedSum = Math.min(Number(order.grandTotal || 0), Math.round(confirmedSumRaw * 100) / 100);
  const newRemaining = Math.max(0, Math.round((Number(order.grandTotal || 0) - confirmedSum) * 100) / 100);
  const meetsRequired = confirmedSum > 0 && (Number(order.advanceAmountRequested || 0) <= 0 || confirmedSum + 0.01 >= Number(order.advanceAmountRequested || 0));

  order.verifiedAdvanceTotal = confirmedSum;
  order.remainingDue = newRemaining;
  order.farmerPaymentConfirmed = confirmedSum > 0;
  if (confirmedSum > 0) order.farmerPaymentConfirmedAt = now;
  if (approved && meetsRequired && ["PAYMENT_PENDING", "PENDING", "ACCEPTED"].includes(order.status)) {
    order.status = "CONFIRMED";
  }
  order.paymentStatus =
    newRemaining <= 0.01 && confirmedSum > 0
      ? "PAID"
      : confirmedSum > 0
      ? "PARTIAL_ADVANCE_VERIFIED"
      : approved
      ? order.paymentStatus
      : "REJECTED";
  order.updatedAt = now;

  const buyerNotif = {
    id: shortId("not"),
    userId: order.buyerId,
    targetRole: "BUYER",
    senderRole: "FARMER",
    orderId: order.id,
    type: approved ? "ORDER_PAYMENT_VERIFIED" : "ORDER_PAYMENT_REJECTED",
    titleBn: approved ? `অগ্রিম পেমেন্ট নিশ্চিত হয়েছে (${order.orderCode})` : `অগ্রিম পেমেন্ট প্রত্যাখ্যাত হয়েছে (${order.orderCode})`,
    messageBn: approved
      ? `কৃষক ${farmer.fullName} ${formatBdt(payment.amount)} (TxID: ${payment.transactionId}) নিশ্চিত করেছেন। মোট যাচাইকৃত অগ্রিম: ${formatBdt(confirmedSum)}, অবশিষ্ট বকেয়া: ${formatBdt(newRemaining)}।`
      : `কারণ: ${payment.rejectionReason}`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: farmer.id,
    actorRole: farmer.role,
    actionBn: approved ? "কৃষক অগ্রিম পেমেন্ট নিশ্চিতকরণ (ওয়েব)" : "কৃষক অগ্রিম পেমেন্ট প্রত্যাখ্যান (ওয়েব)",
    targetEntity: "OrderPayment",
    targetId: payment.id,
    detailsBn: `${order.orderCode} | ${formatBdt(payment.amount)} | VerifiedTotal=${formatBdt(confirmedSum)} | Due=${formatBdt(newRemaining)}`,
    createdAt: now
  };
  state.notifications.unshift(buyerNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`orderPayments/${payment.id}`, payment);
  await rtdbPush(`orders/${order.id}`, order);
  await rtdbPush(`notifications/${buyerNotif.id}`, buyerNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  closeModal();
  showToast(approved ? "পেমেন্ট নিশ্চিত ও অগ্রিম ব্যালেন্স আপডেট হয়েছে!" : "পেমেন্ট প্রত্যাখ্যাত হয়েছে।", approved ? "success" : "info");
  renderCurrentView();
  return true;
}

async function farmerApproveZeroAdvanceOrder(orderId) {
  const farmer = requireActiveMarketplaceUser("শূন্য-অগ্রিম অর্ডার অনুমোদন");
  if (!farmer) return false;
  const order = state.orders.find((o) => o.id === orderId);
  if (!order) return false;
  if (order.farmerId !== farmer.id) {
    showToast("শুধুমাত্র সংশ্লিষ্ট কৃষক এই অর্ডার অনুমোদন করতে পারেন।", "error");
    return false;
  }
  if (!["PAYMENT_PENDING", "PENDING"].includes(order.status)) {
    showToast("এই অর্ডারটি ইতোমধ্যে অনুমোদিত বা সম্পন্ন হয়েছে।", "info");
    return false;
  }
  const now = Date.now();
  order.status = "CONFIRMED";
  order.paymentStatus = "ZERO_ADVANCE_APPROVED";
  order.remainingDue = Math.max(0, Number(order.grandTotal || 0) - Number(order.verifiedAdvanceTotal || 0));
  order.updatedAt = now;

  const buyerNotif = {
    id: shortId("not"),
    userId: order.buyerId,
    targetRole: "BUYER",
    senderRole: "FARMER",
    orderId: order.id,
    type: "ZERO_ADVANCE_APPROVED",
    titleBn: `কৃষক অর্ডার অনুমোদন করেছেন (${order.orderCode})`,
    messageBn: `কৃষক ${farmer.fullName} শূন্য-অগ্রিম অর্ডারটি Confirmed করেছেন। অবশিষ্ট বকেয়া: ${formatBdt(order.remainingDue)}।`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: farmer.id,
    actorRole: farmer.role,
    actionBn: "শূন্য-অগ্রিম অর্ডার অনুমোদন (ওয়েব)",
    targetEntity: "Order",
    targetId: order.id,
    detailsBn: `${order.orderCode} — অবশিষ্ট বকেয়া ${formatBdt(order.remainingDue)}`,
    createdAt: now
  };
  state.notifications.unshift(buyerNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`orders/${order.id}`, order);
  await rtdbPush(`notifications/${buyerNotif.id}`, buyerNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast("শূন্য-অগ্রিম অর্ডার অনুমোদিত ও Confirmed হয়েছে!", "success");
  renderCurrentView();
  return true;
}

async function farmerConfirmPaymentReceived(orderId) {
  const pending = state.orderPayments.filter((p) => p.orderId === orderId && p.status === "PENDING_VERIFICATION");
  if (pending.length) {
    let ok = true;
    for (const p of pending) {
      const res = await farmerVerifyOrderPayment(p.id, true);
      if (!res) ok = false;
    }
    return ok;
  }
  const farmer = requireActiveMarketplaceUser("টাকা প্রাপ্তি নিশ্চিতকরণ");
  if (!farmer) return false;
  const order = state.orders.find((o) => o.id === orderId);
  if (!order) return false;
  if (order.farmerId !== farmer.id) {
    showToast("শুধুমাত্র সংশ্লিষ্ট কৃষক টাকা বুঝে পাওয়ার নিশ্চিতকরণ দিতে পারেন।", "error");
    return false;
  }
  const now = Date.now();
  order.verifiedAdvanceTotal = Number(order.grandTotal || 0);
  order.remainingDue = 0;
  order.farmerPaymentConfirmed = true;
  order.farmerPaymentConfirmedAt = now;
  order.status = "CONFIRMED";
  order.paymentStatus = "PAID";
  order.updatedAt = now;
  saveLocalSnapshot();
  await rtdbPush(`orders/${order.id}`, order);
  showToast("সম্পূর্ণ পেমেন্ট নিশ্চিত ও অর্ডার Confirmed হয়েছে!", "success");
  renderCurrentView();
  return true;
}

async function updateOrderStatus(orderId, newStatus) {
  const actor = getActiveUser();
  const order = state.orders.find((o) => o.id === orderId);
  if (!actor || !order) return;
  if (actor.role !== "ADMIN" && !requireActiveMarketplaceUser("অর্ডার আপডেট")) return;

  const isFarmer = actor.id === order.farmerId;
  const isBuyer = actor.id === order.buyerId;
  const isAdmin = actor.role === "ADMIN";
  if (!isFarmer && !isBuyer && !isAdmin) {
    showToast("এই অর্ডার পরিবর্তনের অনুমতি নেই।", "error");
    return;
  }
  if (isBuyer && !isAdmin && !["CANCELLED", "COMPLETED"].includes(newStatus)) {
    showToast("ক্রেতা শুধুমাত্র অর্ডার বাতিল বা সম্পন্ন করতে পারেন।", "error");
    return;
  }
  if (isBuyer && newStatus === "CANCELLED" && !["PENDING", "PAYMENT_PENDING"].includes(order.status)) {
    showToast("অর্ডার প্রক্রিয়াধীন হলে বাতিল করা যাবে না।", "error");
    return;
  }

  // Strict Security Rule: Order CANNOT be Confirmed or Completed using buyer submission/screenshot alone!
  if (newStatus === "CONFIRMED" || newStatus === "COMPLETED") {
    if (!order.farmerPaymentConfirmed && order.paymentStatus !== "ZERO_ADVANCE_APPROVED") {
      showToast("অগ্রিম বা পেমেন্ট তথ্য কৃষক নিজ অ্যাকাউন্ট দেখে নিশ্চিত করলে অর্ডার Confirmed বা Completed হবে।", "error");
      return;
    }
  }

  const now = Date.now();
  const wasActive = order.status !== "CANCELLED" && order.status !== "REJECTED";
  const restoringStock = wasActive && (newStatus === "CANCELLED" || newStatus === "REJECTED");

  if (restoringStock && order.items) {
    for (const item of Object.values(order.items)) {
      const prod = state.products.find((p) => p.id === item.productId);
      if (prod) {
        prod.availableQuantity = Number(prod.availableQuantity || 0) + Number(item.quantity || 0);
        prod.status = "ACTIVE";
        prod.updatedAt = now;
        await rtdbPush(`products/${prod.id}`, prod);
      }
    }
  }

  order.status = newStatus;
  if (newStatus === "COMPLETED" && order.paymentStatus !== "PAID") {
    order.paymentStatus = "PAID";
  }
  order.updatedAt = now;

  const targetUserId = isFarmer ? order.buyerId : order.farmerId;
  const targetRole = isFarmer ? "BUYER" : "FARMER";
  const notif = {
    id: shortId("not"),
    userId: targetUserId,
    targetRole,
    senderRole: actor.role,
    orderId: order.id,
    type: "ORDER_STATUS",
    titleBn: `অর্ডার আপডেট (${order.orderCode})`,
    messageBn: `অর্ডারের বর্তমান অবস্থা: ${ORDER_STATUS_META[newStatus]?.labelBn || newStatus}`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: actor.id,
    actorRole: actor.role,
    actionBn: "অর্ডার স্ট্যাটাস পরিবর্তন",
    targetEntity: "Order",
    targetId: order.id,
    detailsBn: `${order.orderCode} -> ${newStatus}`,
    createdAt: now
  };

  state.notifications.unshift(notif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`orders/${order.id}`, order);
  await rtdbPush(`notifications/${notif.id}`, notif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast(`অর্ডার অবস্থা: ${ORDER_STATUS_META[newStatus]?.labelBn || newStatus}`, "success");
  renderCurrentView();
}

async function submitReview(orderId, rating, commentBn) {
  const buyer = getActiveUser();
  const order = state.orders.find((o) => o.id === orderId);
  if (!buyer || !order) return;
  const firstProdId = order.items ? Object.values(order.items)[0]?.productId || "" : "";
  const review = {
    id: shortId("rev"),
    orderId: order.id,
    productId: firstProdId,
    farmerId: order.farmerId,
    buyerId: buyer.id,
    buyerName: buyer.fullName,
    rating: Math.min(5, Math.max(1, Number(rating || 5))),
    commentBn: String(commentBn || "").trim(),
    createdAt: Date.now()
  };
  state.reviews.unshift(review);

  const farmerReviews = state.reviews.filter((r) => r.farmerId === order.farmerId);
  const farmer = state.users.find((u) => u.id === order.farmerId);
  if (farmer && farmerReviews.length) {
    const avg = farmerReviews.reduce((sum, r) => sum + Number(r.rating || 5), 0) / farmerReviews.length;
    farmer.rating = Math.round(avg * 10) / 10;
    farmer.reviewCount = farmerReviews.length;
    await rtdbPush(`users/${farmer.id}`, farmer);
  }
  saveLocalSnapshot();
  await rtdbPush(`reviews/${review.id}`, review);
  showToast("আপনার রিভিউ যুক্ত হয়েছে, ধন্যবাদ!", "success");
  closeModal();
  renderCurrentView();
}

async function submitComplaint(orderId, subjectBn, descriptionBn) {
  const reporter = getActiveUser();
  if (!reporter || !subjectBn.trim()) {
    showToast("অভিযোগের বিষয় লিখুন।", "error");
    return;
  }
  const complaint = {
    id: shortId("cmp"),
    orderId: orderId || "",
    reporterId: reporter.id,
    reporterName: reporter.fullName,
    subjectBn: subjectBn.trim(),
    descriptionBn: String(descriptionBn || "").trim(),
    status: "OPEN",
    createdAt: Date.now()
  };
  state.complaints.unshift(complaint);
  saveLocalSnapshot();
  await rtdbPush(`complaints/${complaint.id}`, complaint);
  showToast("অভিযোগ অ্যাডমিন প্যানেলে পাঠানো হয়েছে।", "success");
  closeModal();
  renderCurrentView();
}

async function adminVerifyUser(userId, approved) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") return;
  const person = state.users.find((u) => u.id === userId);
  if (!person) return;
  const isFarmer = person.role === "FARMER";
  const status = approved ? "VERIFIED" : "REJECTED";
  person.verificationStatus = status;

  if (isFarmer) {
    for (const prod of state.products) {
      if (prod.farmerId === userId) {
        prod.farmerVerified = approved;
        await rtdbPush(`products/${prod.id}`, prod);
      }
    }
  }

  const notif = {
    id: shortId("not"),
    userId: userId,
    targetRole: person.role || "BUYER",
    senderRole: "ADMIN",
    orderId: "",
    type: "VERIFICATION",
    titleBn: approved
      ? isFarmer ? "কৃষক পরিচয় যাচাই সম্পন্ন" : "ক্রেতা পরিচয় যাচাই সম্পন্ন"
      : "পরিচয় যাচাই প্রত্যাখ্যাত",
    messageBn: approved
      ? isFarmer
        ? "আপনার কৃষক প্রোফাইল যাচাইকৃত ব্যাজ পেয়েছে।"
        : "অ্যাডমিন আপনার ক্রেতা অ্যাকাউন্ট যাচাই সম্পন্ন করেছেন।"
      : "সঠিক তথ্য দিয়ে পুনরায় আবেদন করুন।",
    isRead: false,
    createdAt: Date.now()
  };
  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: `${isFarmer ? "কৃষক" : "ক্রেতা"} যাচাইকরণ (${status})`,
    targetEntity: "User",
    targetId: userId,
    detailsBn: `${person.fullName} — ${status}`,
    createdAt: Date.now()
  };

  state.notifications.unshift(notif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`users/${userId}`, person);
  await rtdbPush(`notifications/${notif.id}`, notif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast(
    approved
      ? `${isFarmer ? "কৃষক" : "ক্রেতা"} যাচাই সম্পন্ন হয়েছে!`
      : "আবেদন বাতিল করা হয়েছে।",
    "success"
  );
  renderCurrentView();
}

async function adminSendNotification({ targetUserId, targetRole, titleBn, messageBn }) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র সুপার অ্যাডমিন নোটিফিকেশন পাঠাতে পারেন।", "error");
    return false;
  }
  if (!titleBn.trim() || !messageBn.trim()) {
    showToast("নোটিফিকেশনের শিরোনাম ও বার্তা লিখুন।", "error");
    return false;
  }
  const now = Date.now();
  const notif = {
    id: shortId("not"),
    userId: targetUserId || "ALL",
    targetRole: targetRole || "ALL",
    senderRole: "ADMIN",
    orderId: "",
    type: "ADMIN_BROADCAST",
    titleBn: titleBn.trim(),
    messageBn: messageBn.trim(),
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: "অ্যাডমিন নোটিফিকেশন প্রেরণ",
    targetEntity: "Notification",
    targetId: notif.id,
    detailsBn: `প্রাপক: ${notif.userId} (${notif.targetRole}) — ${notif.titleBn}`,
    createdAt: now
  };
  state.notifications.unshift(notif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`notifications/${notif.id}`, notif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast("নোটিফিকেশন সফলভাবে পাঠানো হয়েছে (অ্যাপ ও ওয়েব লাইভ)!", "success");
  renderCurrentView();
  return true;
}

async function adminAddCategory(nameBn, nameEn, emoji) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN" || !nameBn.trim()) return;
  const cat = {
    id: shortId("cat"),
    nameBn: nameBn.trim(),
    nameEn: (nameEn || nameBn).trim(),
    groupKey: "OTHER",
    emoji: (emoji || "🌱").trim(),
    sortOrder: 99,
    isActive: true
  };
  state.categories.push(cat);
  saveLocalSnapshot();
  await rtdbPush(`categories/${cat.id}`, cat);
  showToast("নতুন ক্যাটাগরি যুক্ত হয়েছে!", "success");
  renderCurrentView();
}

async function adminAddLocation(divisionBn, districtBn, upazilaBn, unionBn, villageBn) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN" || !districtBn.trim() || !upazilaBn.trim()) return;
  const loc = {
    id: shortId("loc"),
    divisionBn: divisionBn.trim(),
    districtBn: districtBn.trim(),
    upazilaBn: upazilaBn.trim(),
    unionBn: String(unionBn || "").trim(),
    villageBn: String(villageBn || "").trim(),
    isActive: true
  };
  state.locations.push(loc);
  saveLocalSnapshot();
  await rtdbPush(`locations/${loc.id}`, loc);
  showToast("নতুন এলাকা যুক্ত হয়েছে!", "success");
  renderCurrentView();
}

async function adminResolveComplaint(complaintId) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") return;
  const c = state.complaints.find((x) => x.id === complaintId);
  if (!c) return;
  c.status = "RESOLVED";
  saveLocalSnapshot();
  await rtdbPush(`complaints/${c.id}`, c);
  showToast("অভিযোগটি নিষ্পত্তি হিসেবে চিহ্নিত।", "success");
  renderCurrentView();
}

async function adminVerifyProduct(productId, approved, reason = "") {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র অ্যাডমিন ফসল যাচাই ও অনুমোদন করতে পারেন।", "error");
    return false;
  }
  const prod = state.products.find((p) => p.id === productId);
  if (!prod) return false;
  const now = Date.now();
  const nextStatus = approved
    ? Number(prod.availableQuantity || 0) > 0
      ? "ACTIVE"
      : "OUT_OF_STOCK"
    : "REJECTED";
  prod.status = nextStatus;
  prod.updatedAt = now;

  const cleanReason = String(reason || "").trim();
  const farmerNotif = {
    id: shortId("not"),
    userId: prod.farmerId,
    targetRole: "FARMER",
    senderRole: "ADMIN",
    orderId: "",
    type: "PRODUCT_VERIFICATION",
    titleBn: approved
      ? `আপনার ফসল "${prod.nameBn}" অনুমোদিত হয়েছে ✓`
      : `আপনার ফসল "${prod.nameBn}" প্রত্যাখ্যাত হয়েছে`,
    messageBn: approved
      ? `অ্যাডমিন যাচাইয়ের পর আপনার "${prod.nameBn}" ফসলটি সবার জন্য উন্মুক্ত করা হয়েছে।`
      : `অ্যাডমিন আপনার "${prod.nameBn}" ফসলটি অনুমোদন করেননি। ${cleanReason ? "কারণ: " + cleanReason : ""}`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: approved ? "ফসল অনুমোদন (অ্যাডমিন)" : "ফসল প্রত্যাখ্যান (অ্যাডমিন)",
    targetEntity: "Product",
    targetId: prod.id,
    detailsBn: `${prod.nameBn} (${prod.farmerName}) -> ${nextStatus}${cleanReason ? " (" + cleanReason + ")" : ""}`,
    createdAt: now
  };
  state.notifications.unshift(farmerNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`products/${prod.id}`, prod);
  await rtdbPush(`notifications/${farmerNotif.id}`, farmerNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  closeModal();
  showToast(approved ? `"${prod.nameBn}" অনুমোদিত ও সবার জন্য প্রকাশিত হয়েছে!` : `"${prod.nameBn}" প্রত্যাখ্যান করা হয়েছে।`, approved ? "success" : "info");
  renderCurrentView();
  return true;
}

async function adminUpdateProduct(productId, form) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র অ্যাডমিন ফসল সম্পাদনা করতে পারেন।", "error");
    return false;
  }
  const prod = state.products.find((p) => p.id === productId);
  if (!prod) return false;

  const nameBn = String(form.nameBn || "").trim();
  const price = Number(form.pricePerUnit);
  const qty = Number(form.availableQuantity);
  if (!nameBn || Number.isNaN(price) || price <= 0 || Number.isNaN(qty) || qty < 0) {
    showToast("ফসলের নাম, সঠিক দাম ও মজুত দিন।", "error");
    return false;
  }
  const cat = state.categories.find((c) => c.id === form.categoryId);
  const requestedStatus = String(form.status || prod.status || "ACTIVE");
  const normalizedStatus =
    requestedStatus === "ACTIVE" && qty <= 0
      ? "OUT_OF_STOCK"
      : ["PENDING_APPROVAL", "ACTIVE", "REJECTED", "OUT_OF_STOCK"].includes(requestedStatus)
      ? requestedStatus
      : prod.status;

  const now = Date.now();
  prod.nameBn = nameBn;
  prod.nameEn = nameBn;
  if (cat) {
    prod.categoryId = cat.id;
    prod.categoryNameBn = cat.nameBn;
  }
  prod.pricePerUnit = price;
  prod.availableQuantity = qty;
  prod.unit = "মণ";
  prod.isWholesale = true;
  prod.descriptionBn = String(form.descriptionBn ?? prod.descriptionBn ?? "").trim();
  prod.status = normalizedStatus;
  prod.updatedAt = now;

  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: "ফসল সম্পাদনা (অ্যাডমিন)",
    targetEntity: "Product",
    targetId: prod.id,
    detailsBn: `${prod.nameBn} — ${formatBdt(prod.pricePerUnit)}/মণ, মজুত ${formatQty(prod.availableQuantity)} মণ (${prod.status})`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`products/${prod.id}`, prod);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  closeModal();
  showToast(`"${prod.nameBn}" তথ্য সফলভাবে আপডেট হয়েছে!`, "success");
  renderCurrentView();
  return true;
}

async function deleteProduct(productId) {
  const actor = getActiveUser();
  const prod = state.products.find((p) => p.id === productId);
  if (!actor || !prod) return false;
  if (actor.role !== "ADMIN" && prod.farmerId !== actor.id) {
    showToast("শুধুমাত্র অ্যাডমিন বা সংশ্লিষ্ট কৃষক এই ফসল মুছতে পারেন।", "error");
    return false;
  }
  const now = Date.now();
  state.products = state.products.filter((p) => p.id !== productId);
  state.cart = state.cart.filter((c) => c.productId !== productId);

  const audit = {
    id: shortId("aud"),
    actorId: actor.id,
    actorRole: actor.role,
    actionBn: actor.role === "ADMIN" ? "ফসল মুছে ফেলা (অ্যাডমিন)" : "ফসল মুছে ফেলা (কৃষক)",
    targetEntity: "Product",
    targetId: productId,
    detailsBn: `${prod.nameBn} (${prod.farmerName}) মুছে ফেলা হয়েছে`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();
  updateHeaderChrome();

  await rtdbDelete(`products/${productId}`);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  closeModal();
  showToast(`"${prod.nameBn}" তালিকা থেকে মুছে ফেলা হয়েছে।`, "info");
  renderCurrentView();
  return true;
}

async function markVisibleNotificationsRead() {
  const visible = getVisibleNotifications().filter((n) => !n.isRead);
  for (const n of visible) {
    n.isRead = true;
    await rtdbPush(`notifications/${n.id}`, n);
  }
  saveLocalSnapshot();
  updateHeaderChrome();
  renderCurrentView();
}

function exportCsvReport() {
  const header = "OrderCode,Farmer,Buyer,Division,District,Upazila,ProductTotalBDT,DeliveryChargeBDT,GrandTotalBDT,PaymentMethod,Status\n";
  const rows = state.orders
    .map((o) =>
      [
        o.orderCode,
        `"${(o.farmerName || "").replace(/"/g, '""')}"`,
        `"${(o.buyerName || "").replace(/"/g, '""')}"`,
        `"${o.division || ""}"`,
        `"${o.district || ""}"`,
        `"${o.upazila || ""}"`,
        o.productTotal || 0,
        o.deliveryCharge || 0,
        o.grandTotal || 0,
        o.paymentMethod || "",
        o.status || ""
      ].join(",")
    )
    .join("\n");
  const blob = new Blob(["\uFEFF" + header + rows], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `krishi_bondhu_orders_${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

// ---------------------- Ad Monetization Engine (Multi-Network) ----------------------

function safeHttpUrl(url) {
  const s = String(url || "").trim();
  return s.startsWith("https://") || s.startsWith("http://") ? s : "";
}

async function adminSaveAdConfig(nextConfig) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র সুপার অ্যাডমিন বিজ্ঞাপন সেটিংস সংরক্ষণ করতে পারেন।", "error");
    return false;
  }
  const now = Date.now();
  const merged = { ...DEFAULT_AD_CONFIG, ...nextConfig, updatedAt: now };
  state.adConfig = merged;
  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: "বিজ্ঞাপন ও মনিটাইজেশন আপডেট (ওয়েব)",
    targetEntity: "AdConfig",
    targetId: "adConfig",
    detailsBn: `Ads: ${merged.adsEnabled ? "চালু" : "বন্ধ"}, LevelPlay AppKey: ${merged.mediationAppKey ? "সেট" : "ফাঁকা"}, AdSense: ${merged.adsenseEnabled ? "চালু" : "বন্ধ"}, Adsterra: ${merged.adsterraEnabled ? "চালু" : "বন্ধ"}, Monetag: ${merged.monetagEnabled ? "চালু" : "বন্ধ"}`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush("adConfig", merged);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  lastAdScriptSignature = "";
  showToast("বিজ্ঞাপন সেটিংস সফলভাবে সেভ ও লাইভ হয়েছে!", "success");
  renderCurrentView();
  return true;
}

async function adminSaveDailyTip(tipDraft) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র সুপার অ্যাডমিন ডেইলি টিপস যোগ বা সম্পাদনা করতে পারেন।", "error");
    return false;
  }
  const titleBn = String(tipDraft?.titleBn || "").trim();
  const contentBn = String(tipDraft?.contentBn || "").trim();
  if (!titleBn || !contentBn) {
    showToast("ডেইলি টিপসের শিরোনাম এবং বিবরণ আবশ্যক।", "error");
    return false;
  }
  const now = Date.now();
  const existing = state.dailyTips.find((t) => t.id === tipDraft.id);
  const id = String(tipDraft.id || "").trim() || shortId("tip");
  const item = {
    id,
    titleBn,
    contentBn,
    categoryBn: String(tipDraft?.categoryBn || "কৃষি পরামর্শ").trim() || "কৃষি পরামর্শ",
    emoji: String(tipDraft?.emoji || "🌾").trim() || "🌾",
    isActive: tipDraft?.isActive !== false,
    createdAt: existing?.createdAt || Number(tipDraft?.createdAt || now),
    updatedAt: now
  };
  state.dailyTipsManaged = true;
  state.dailyTips = [item, ...state.dailyTips.filter((t) => t.id !== id)]
    .sort((a, b) => (b.updatedAt || b.createdAt || 0) - (a.updatedAt || a.createdAt || 0));

  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: existing ? "ডেইলি টিপস সম্পাদনা (ওয়েব)" : "নতুন ডেইলি টিপস যোগ (ওয়েব)",
    targetEntity: "DailyTip",
    targetId: id,
    detailsBn: `${item.emoji} ${item.titleBn} (${item.isActive ? "সক্রিয়" : "বন্ধ"})`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);

  await rtdbPush("dailyTipsMeta", { managed: true, updatedAt: now });
  await rtdbPush(`dailyTips/${id}`, item);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast("ডেইলি টিপস সফলভাবে সংরক্ষণ ও সিঙ্ক হয়েছে!", "success");
  renderCurrentView();
  return true;
}

async function adminDeleteDailyTip(tipId) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র সুপার অ্যাডমিন ডেইলি টিপস মুছতে পারেন।", "error");
    return false;
  }
  const target = state.dailyTips.find((t) => t.id === tipId);
  if (!target) return false;
  const now = Date.now();
  state.dailyTipsManaged = true;
  state.dailyTips = state.dailyTips.filter((t) => t.id !== tipId);
  if (state.revealedDailyTipId === tipId) state.revealedDailyTipId = "";

  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: "ডেইলি টিপস মুছে ফেলা (ওয়েব)",
    targetEntity: "DailyTip",
    targetId: tipId,
    detailsBn: `${target.titleBn} মুছে ফেলা হয়েছে`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);

  await rtdbPush("dailyTipsMeta", { managed: true, updatedAt: now });
  await rtdbDelete(`dailyTips/${tipId}`);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast("ডেইলি টিপস মুছে ফেলা হয়েছে।", "info");
  renderCurrentView();
  return true;
}

function renderChunkedGridWithFeedAds(items, renderCardFn, chunkSize = 4, gridClass = "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4") {
  if (!items || !items.length) return "";
  const blocks = [];
  for (let i = 0; i < items.length; i += chunkSize) {
    const chunk = items.slice(i, i + chunkSize);
    blocks.push(`<div class="${gridClass}">${chunk.map(renderCardFn).join("")}</div>`);
    if (i + chunkSize < items.length) {
      const adHtml = renderAdPlacement("feed");
      if (adHtml) blocks.push(adHtml);
    }
  }
  return `<div class="space-y-4">${blocks.join("")}</div>`;
}

function renderDailyTipsCard() {
  const activeTips = state.dailyTips.filter((t) => t.isActive !== false);
  const currentTip = activeTips.find((t) => t.id === state.revealedDailyTipId) || activeTips[0] || null;
  return `
    <section class="bg-gradient-to-r from-amber-50 via-[#EAF7EF] to-emerald-50 rounded-3xl p-5 sm:p-6 border border-emerald-200 shadow-sm space-y-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <span class="text-xl">💡</span>
          <div>
            <h3 class="font-bold text-[#105B34] text-base">আজকের পাইকারি কৃষি ডেইলি টিপস (${activeTips.length}টি)</h3>
            <p class="text-[11px] text-gray-600">অ্যাডমিন প্যানেল থেকে রিয়েল-টাইম আপডেটকৃত সংরক্ষণ ও বাজার পরামর্শ</p>
          </div>
        </div>
        ${
          activeTips.length > 1
            ? `<button type="button" onclick="window.kbApp.revealNextDailyTip()" class="px-3.5 py-2 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white text-xs font-bold shadow-sm">
                অন্য টিপস দেখুন →
              </button>`
            : ""
        }
      </div>
      ${
        !currentTip
          ? `<div class="bg-white/80 rounded-2xl p-4 text-xs text-gray-600 border border-emerald-100">বর্তমানে কোনো সক্রিয় ডেইলি টিপস নেই।</div>`
          : `<div class="bg-white rounded-2xl p-4 border border-emerald-100 space-y-1.5">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <span class="font-bold text-sm text-[#105B34]">${escapeHtml(currentTip.emoji || "🌾")} ${escapeHtml(currentTip.titleBn)}</span>
                <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-[#EAF7EF] text-[#105B34]">${escapeHtml(currentTip.categoryBn || "কৃষি পরামর্শ")}</span>
              </div>
              <p class="text-xs sm:text-sm text-gray-700 leading-relaxed">${escapeHtml(currentTip.contentBn)}</p>
            </div>`
      }
    </section>
  `;
}

function syncExternalAdNetworkScripts() {
  const cfg = state.adConfig || DEFAULT_AD_CONFIG;
  const suppressed = isCurrentUserAdsSuppressed();
  const sig = JSON.stringify({
    e: cfg.adsEnabled && !suppressed,
    ac: cfg.adsenseEnabled ? cfg.adsenseClient : "",
    asb: cfg.adsterraEnabled ? cfg.adsterraSocialBarUrl : "",
    m: cfg.monetagEnabled ? `${cfg.monetagZoneId}|${cfg.monetagScriptUrl}` : ""
  });
  if (sig === lastAdScriptSignature) return;
  lastAdScriptSignature = sig;

  const scriptsRoot = document.getElementById("ad-scripts-root");
  if (!scriptsRoot) return;
  scriptsRoot.innerHTML = "";
  if (!cfg.adsEnabled || suppressed) return;

  if (cfg.adsenseEnabled && cfg.adsenseClient) {
    const existing = document.querySelector(`script[data-kb-ad-net="adsense"]`);
    if (existing) existing.remove();
    const s = document.createElement("script");
    s.async = true;
    s.crossOrigin = "anonymous";
    s.dataset.kbAdNet = "adsense";
    s.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${encodeURIComponent(cfg.adsenseClient.trim())}`;
    document.head.appendChild(s);
  }

  const socialBarUrl = safeHttpUrl(cfg.adsterraSocialBarUrl);
  if (cfg.adsterraEnabled && socialBarUrl) {
    const s = document.createElement("script");
    s.async = true;
    s.src = socialBarUrl;
    scriptsRoot.appendChild(s);
  }

  const monetagUrl = safeHttpUrl(cfg.monetagScriptUrl);
  if (cfg.monetagEnabled && (monetagUrl || cfg.monetagZoneId)) {
    const s = document.createElement("script");
    s.async = true;
    if (cfg.monetagZoneId) s.dataset.zone = cfg.monetagZoneId.trim();
    s.src = monetagUrl || `https://alwingulla.com/88/tag.min.js`;
    scriptsRoot.appendChild(s);
  }
}

function renderAdPlacement(slotName) {
  const cfg = state.adConfig || DEFAULT_AD_CONFIG;
  if (!cfg.adsEnabled || isCurrentUserAdsSuppressed()) return "";

  const parts = [];
  const labelBadge = cfg.showAdsLabel
    ? `<span class="inline-block px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-[10px] font-bold uppercase tracking-wider">বিজ্ঞাপন • Sponsored</span>`
    : "";

  // 1. Google AdSense Slot
  const adsenseSlotId =
    slotName === "top"
      ? cfg.adsenseTopSlot
      : slotName === "feed"
      ? cfg.adsenseFeedSlot
      : cfg.adsenseFooterSlot;
  if (cfg.adsenseEnabled && cfg.adsenseClient && adsenseSlotId) {
    parts.push(`
      <div class="bg-white rounded-2xl border border-emerald-100 p-3 text-center shadow-sm">
        ${labelBadge ? `<div class="mb-1.5 text-left">${labelBadge} <span class="text-[10px] text-gray-400">Google AdSense</span></div>` : ""}
        <ins class="adsbygoogle"
          style="display:block"
          data-ad-client="${escapeHtml(cfg.adsenseClient)}"
          data-ad-slot="${escapeHtml(adsenseSlotId)}"
          data-ad-format="auto"
          data-full-width-responsive="true"></ins>
      </div>
    `);
  }

  // 2. Adsterra Banner Slot
  const adsterraInvokeUrl = safeHttpUrl(cfg.adsterraScriptUrl);
  if (cfg.adsterraEnabled && (cfg.adsterraBannerKey || adsterraInvokeUrl)) {
    parts.push(`
      <div class="bg-white rounded-2xl border border-emerald-100 p-3 text-center shadow-sm" data-adsterra-slot="${escapeHtml(slotName)}">
        ${labelBadge ? `<div class="mb-1 text-left">${labelBadge} <span class="text-[10px] text-gray-400">Adsterra</span></div>` : ""}
        <div id="adsterra-box-${escapeHtml(slotName)}" class="min-h-[60px] flex items-center justify-center text-xs text-gray-500">
          Adsterra Zone Ready (${escapeHtml(cfg.adsterraBannerKey || "Active")})
        </div>
      </div>
    `);
  }

  // 3. Monetag / PropellerAds Banner Container
  if (cfg.monetagEnabled && (cfg.monetagZoneId || cfg.monetagScriptUrl)) {
    parts.push(`
      <div class="bg-white rounded-2xl border border-emerald-100 p-3 text-center shadow-sm" data-monetag-zone="${escapeHtml(cfg.monetagZoneId)}">
        ${labelBadge ? `<div class="mb-1 text-left">${labelBadge} <span class="text-[10px] text-gray-400">Monetag</span></div>` : ""}
        <div class="text-xs text-gray-500 py-2">Monetag Zone Active (${escapeHtml(cfg.monetagZoneId || "Connected")})</div>
      </div>
    `);
  }

  // 4. Custom HTML / Ezoic / Media.net Tag (Admin configured)
  const customHtml =
    slotName === "top"
      ? cfg.customTopHtml
      : slotName === "feed"
      ? cfg.customFeedHtml || cfg.customTopHtml
      : cfg.customFooterHtml || cfg.customTopHtml;
  if (cfg.customAdEnabled && customHtml && String(customHtml).trim()) {
    parts.push(`
      <div class="bg-white rounded-2xl border border-emerald-100 p-3 shadow-sm">
        ${labelBadge ? `<div class="mb-1.5 flex items-center justify-between">${labelBadge}<span class="text-[10px] text-gray-400">${escapeHtml(cfg.customAdName || "Custom Ad")}</span></div>` : ""}
        <div class="overflow-x-auto">${customHtml}</div>
      </div>
    `);
  }

  // 5. Direct Sponsor Card (Always clean M3/Tailwind card on feed/footer)
  if (cfg.sponsorEnabled && cfg.sponsorTitle && (slotName === "feed" || slotName === "footer" || parts.length === 0)) {
    const imgUrl = safeHttpUrl(cfg.sponsorImageUrl);
    const targetUrl = safeHttpUrl(cfg.sponsorTargetUrl);
    parts.push(`
      <div class="bg-gradient-to-r from-[#EAF7EF] via-white to-amber-50 rounded-2xl border border-emerald-200 p-4 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div class="flex items-center gap-3.5">
          ${
            imgUrl
              ? `<img src="${escapeHtml(imgUrl)}" alt="${escapeHtml(cfg.sponsorTitle)}" class="w-16 h-16 rounded-xl object-cover border border-emerald-200 shrink-0" />`
              : `<div class="w-12 h-12 rounded-xl bg-[#168447] text-white flex items-center justify-center text-xl shrink-0">📣</div>`
          }
          <div class="space-y-0.5">
            ${labelBadge}
            <h4 class="font-bold text-sm sm:text-base text-[#105B34]">${escapeHtml(cfg.sponsorTitle)}</h4>
            ${cfg.sponsorSubtitle ? `<p class="text-xs text-gray-600">${escapeHtml(cfg.sponsorSubtitle)}</p>` : ""}
          </div>
        </div>
        ${
          targetUrl
            ? `<a href="${escapeHtml(targetUrl)}" target="_blank" rel="noopener noreferrer" class="shrink-0 px-4 py-2 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white text-xs font-bold text-center">${escapeHtml(cfg.sponsorCtaText || "বিস্তারিত দেখুন")} ↗</a>`
            : `<button onclick="window.kbApp.navigate('browse')" class="shrink-0 px-4 py-2 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white text-xs font-bold">${escapeHtml(cfg.sponsorCtaText || "ফসল দেখুন")}</button>`
        }
      </div>
    `);
  }

  if (!parts.length) return "";
  return `<section class="kb-ad-placement space-y-2" data-slot="${escapeHtml(slotName)}">${parts.join("")}</section>`;
}

function mountPageAdPlacements() {
  syncExternalAdNetworkScripts();
  const cfg = state.adConfig || DEFAULT_AD_CONFIG;
  const suppressed = isCurrentUserAdsSuppressed();
  const topEl = document.getElementById("ad-slot-top");
  const footerEl = document.getElementById("ad-slot-footer");

  if (topEl) topEl.innerHTML = cfg.adsEnabled && !suppressed ? renderAdPlacement("top") : "";
  if (footerEl) footerEl.innerHTML = cfg.adsEnabled && !suppressed ? renderAdPlacement("footer") : "";
  if (suppressed) return;

  if (cfg.adsEnabled && cfg.adsenseEnabled && cfg.adsenseClient && typeof window !== "undefined") {
    try {
      const insNodes = document.querySelectorAll("ins.adsbygoogle:not([data-adsbygoogle-status])");
      if (insNodes.length) {
        window.adsbygoogle = window.adsbygoogle || [];
        insNodes.forEach(() => window.adsbygoogle.push({}));
      }
    } catch (_) {}
  }

  const invokeUrl = safeHttpUrl(cfg.adsterraScriptUrl);
  if (cfg.adsEnabled && cfg.adsterraEnabled && invokeUrl) {
    ["top", "feed", "footer"].forEach((slot) => {
      const box = document.getElementById(`adsterra-box-${slot}`);
      if (!box || box.dataset.loaded === "1") return;
      box.dataset.loaded = "1";
      box.innerHTML = "";
      if (cfg.adsterraBannerKey) {
        const cfgScript = document.createElement("script");
        cfgScript.text = `atOptions = { 'key': ${JSON.stringify(cfg.adsterraBannerKey)}, 'format': 'iframe', 'height': 90, 'width': 728, 'params': {} };`;
        box.appendChild(cfgScript);
      }
      const invokeScript = document.createElement("script");
      invokeScript.async = true;
      invokeScript.src = invokeUrl;
      box.appendChild(invokeScript);
    });
  }
}

// ---------------------- Render Helpers ----------------------

function normBn(v) {
  return String(v || "").trim().normalize("NFC").toLowerCase();
}

function getUpazilaOptions(division, district) {
  const normDiv = normBn(division);
  const normDist = normBn(district);
  const map = new Map();
  const add = (val) => {
    const clean = String(val || "").trim().normalize("NFC");
    if (!clean) return;
    const key = clean.toLowerCase();
    if (!map.has(key)) map.set(key, clean);
  };
  if (normDist) {
    const distObj = BD_DISTRICTS.find((d) => normBn(d.districtBn) === normDist);
    (distObj?.upazilas || []).forEach(add);
  } else if (normDiv) {
    BD_DISTRICTS.filter((d) => normBn(d.divisionBn) === normDiv).forEach((d) => (d.upazilas || []).forEach(add));
  }
  for (const loc of state.locations || []) {
    if (normDiv && normBn(loc.divisionBn) !== normDiv) continue;
    if (normDist && normBn(loc.districtBn) !== normDist) continue;
    add(loc.upazilaBn);
  }
  for (const u of state.users || []) {
    if (u.role !== "FARMER") continue;
    if (normDiv && normBn(u.division) !== normDiv) continue;
    if (normDist && normBn(u.district) !== normDist) continue;
    add(u.upazila);
  }
  for (const p of state.products || []) {
    if (normDiv && normBn(p.division) !== normDiv) continue;
    if (normDist && normBn(p.district) !== normDist) continue;
    add(p.upazila);
  }
  const arr = Array.from(map.values());
  return normDist ? arr : arr.sort((a, b) => a.localeCompare(b));
}

function getUnionOptions(district, upazila, division) {
  const normDiv = normBn(division);
  const normDist = normBn(district);
  const normUpz = normBn(upazila);
  const map = new Map();
  const add = (val) => {
    const clean = String(val || "").trim().normalize("NFC");
    if (!clean) return;
    const key = clean.toLowerCase();
    if (!map.has(key)) map.set(key, clean);
  };
  for (const loc of state.locations || []) {
    if (normDiv && normBn(loc.divisionBn) !== normDiv) continue;
    if (normDist && normBn(loc.districtBn) !== normDist) continue;
    if (normUpz && normBn(loc.upazilaBn) !== normUpz) continue;
    add(loc.unionBn);
  }
  for (const u of state.users || []) {
    if (normDiv && normBn(u.division) !== normDiv) continue;
    if (normDist && normBn(u.district) !== normDist) continue;
    if (normUpz && normBn(u.upazila) !== normUpz) continue;
    add(u.unionName);
  }
  for (const p of state.products || []) {
    if (normDiv && normBn(p.division) !== normDiv) continue;
    if (normDist && normBn(p.district) !== normDist) continue;
    if (normUpz && normBn(p.upazila) !== normUpz) continue;
    add(p.unionName);
  }
  return Array.from(map.values()).sort((a, b) => a.localeCompare(b));
}

function getFilteredProducts() {
  const f = state.filters;
  const q = normBn(f.query);
  const divN = normBn(f.division);
  const distN = normBn(f.district);
  const upzN = normBn(f.upazila);
  const unionN = normBn(f.union);
  const list = state.products.filter((p) => {
    if (p.status !== "ACTIVE") return false;
    const matchQ =
      !q ||
      normBn(p.nameBn).includes(q) ||
      normBn(p.nameEn).includes(q) ||
      normBn(p.descriptionBn).includes(q) ||
      normBn(p.farmerName).includes(q) ||
      normBn(p.division).includes(q) ||
      normBn(p.district).includes(q) ||
      normBn(p.upazila).includes(q) ||
      normBn(p.unionName).includes(q) ||
      normBn(p.pickupAddress).includes(q) ||
      normBn(p.categoryNameBn).includes(q);
    const matchCat = !f.categoryId || p.categoryId === f.categoryId;
    const matchDiv = !divN || normBn(p.division) === divN;
    const matchDist = !distN || normBn(p.district) === distN;
    const matchUpz = !upzN || normBn(p.upazila) === upzN;
    const matchUnion = !unionN || normBn(p.unionName) === unionN;
    return matchQ && matchCat && matchDiv && matchDist && matchUpz && matchUnion;
  });

  if (f.sort === "PRICE_LOW_HIGH") return [...list].sort((a, b) => a.pricePerUnit - b.pricePerUnit);
  if (f.sort === "PRICE_HIGH_LOW") return [...list].sort((a, b) => b.pricePerUnit - a.pricePerUnit);
  return [...list].sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
}

function getFilteredFarmers() {
  const f = state.filters;
  const q = normBn(f.query);
  const divN = normBn(f.division);
  const distN = normBn(f.district);
  const upzN = normBn(f.upazila);
  const unionN = normBn(f.union);
  return state.users.filter((u) => {
    if (u.role !== "FARMER") return false;
    const matchQ =
      !q ||
      normBn(u.fullName).includes(q) ||
      normBn(u.division).includes(q) ||
      normBn(u.district).includes(q) ||
      normBn(u.upazila).includes(q) ||
      normBn(u.unionName).includes(q) ||
      normBn(u.village).includes(q) ||
      normBn(u.farmBio).includes(q);
    const matchDiv = !divN || normBn(u.division) === divN;
    const matchDist = !distN || normBn(u.district) === distN;
    const matchUpz = !upzN || normBn(u.upazila) === upzN;
    const matchUnion = !unionN || normBn(u.unionName) === unionN;
    return matchQ && matchDiv && matchDist && matchUpz && matchUnion;
  });
}

function renderProductCard(p) {
  const user = getActiveUser();
  const isFarmerViewer = user && user.role === "FARMER";
  const isOwnProduct = isFarmerViewer && p.farmerId === user.id;
  const cat = state.categories.find((c) => c.id === p.categoryId);
  const emoji = cat?.emoji || "🌾";
  const kgTotal = formatQty(Number(p.availableQuantity || 0) * MON_TO_KG);
  const imgHtml = p.imageUri
    ? `<img src="${escapeHtml(p.imageUri)}" alt="${escapeHtml(p.nameBn)}" class="w-full h-40 object-cover" onerror="this.style.display='none'" />`
    : `<div class="w-full h-40 bg-[#EAF7EF] flex flex-col items-center justify-center text-4xl">${emoji}<span class="text-xs text-[#105B34] font-semibold mt-1">${escapeHtml(p.categoryNameBn || "")}</span></div>`;

  return `
    <div class="bg-white rounded-3xl border border-emerald-100 shadow-sm hover:shadow-md transition overflow-hidden flex flex-col">
      <button onclick="window.kbApp.navigate('product-detail', { id: '${escapeHtml(p.id)}' })" class="text-left block w-full relative">
        ${imgHtml}
        <span class="absolute top-3 left-3 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500 text-white shadow">পাইকারি মাত্র</span>
        ${
          p.status !== "ACTIVE"
            ? `<span class="absolute top-3 right-3 px-2.5 py-0.5 rounded-full text-[11px] font-bold shadow ${(PRODUCT_STATUS_META[p.status] || PRODUCT_STATUS_META.PENDING_APPROVAL).color}">${escapeHtml((PRODUCT_STATUS_META[p.status] || PRODUCT_STATUS_META.PENDING_APPROVAL).labelBn)}</span>`
            : p.farmerVerified
            ? `<span class="absolute top-3 right-3 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-[#168447] text-white shadow">✓ যাচাইকৃত</span>`
            : ""
        }
      </button>
      <div class="p-4 flex-1 flex flex-col justify-between gap-3">
        <div>
          <button onclick="window.kbApp.navigate('product-detail', { id: '${escapeHtml(p.id)}' })" class="text-left w-full">
            <h3 class="font-bold text-base text-[#202820] hover:text-[#168447] line-clamp-1">${escapeHtml(p.nameBn)}</h3>
          </button>
          <p class="text-xs text-gray-600 mt-0.5">👨‍🌾 ${escapeHtml(p.farmerName)} • 📍 ${escapeHtml(p.unionName ? p.unionName + ", " : "")}${escapeHtml(p.upazila || "")}, ${escapeHtml(p.district || "")}</p>
          <div class="mt-2 flex items-baseline justify-between gap-1">
            <span class="text-lg font-extrabold text-[#168447]">${formatBdt(p.pricePerUnit)}<span class="text-xs font-medium text-gray-500"> / মণ</span></span>
            <span class="text-[11px] font-medium text-emerald-700 bg-[#EAF7EF] px-2 py-0.5 rounded-full">মজুত: ${formatQty(p.availableQuantity)} মণ (${kgTotal} কেজি)</span>
          </div>
          <p class="text-[10px] text-gray-500 mt-0.5">১ মণ = ${MON_TO_KG} কেজি</p>
        </div>
        <div class="flex gap-2">
          ${
            isOwnProduct
              ? `<button onclick="window.kbApp.navigate('farmer-dashboard', { tab: 'products' })" class="flex-1 py-2.5 rounded-xl bg-amber-50 text-amber-800 border border-amber-200 text-xs font-bold">দাম ও মজুত সম্পাদনা</button>`
              : isFarmerViewer
              ? `<button onclick="window.kbApp.navigate('product-detail', { id: '${escapeHtml(p.id)}' })" class="flex-1 py-2.5 rounded-xl bg-[#EAF7EF] text-[#105B34] border border-emerald-200 text-xs font-bold">বাজার দর দেখুন</button>`
              : `<button onclick="window.kbApp.addToCart('${escapeHtml(p.id)}', 1)" class="flex-1 py-2.5 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white text-xs font-bold transition">+ কার্টে যোগ করুন</button>`
          }
          <button onclick="window.kbApp.navigate('product-detail', { id: '${escapeHtml(p.id)}' })" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-xs font-semibold text-[#105B34] hover:bg-[#EAF7EF]">
            বিস্তারিত
          </button>
        </div>
      </div>
    </div>
  `;
}

// ---------------------- Page Views ----------------------

function renderFarmerHomeWorkspace(user) {
  const myProducts = state.products.filter((p) => p.farmerId === user.id);
  const activeMyProducts = myProducts.filter((p) => p.status === "ACTIVE");
  const myOrders = state.orders.filter((o) => o.farmerId === user.id);
  const pendingOrders = myOrders.filter((o) => o.status === "PENDING");
  const completedOrders = myOrders.filter((o) => o.status === "COMPLETED");
  const totalSales = myOrders.filter((o) => !["CANCELLED", "REJECTED"].includes(o.status)).reduce((s, o) => s + Number(o.productTotal || 0), 0);

  return `
    <div class="space-y-7">
      <!-- Farmer Studio Hero -->
      <section class="rounded-3xl bg-gradient-to-br from-[#105B34] via-[#168447] to-emerald-600 text-white shadow-lg p-6 sm:p-8 space-y-6">
        <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div class="space-y-2">
            <div class="inline-flex items-center gap-2">
              <span class="px-3 py-1 rounded-full bg-white/15 text-xs font-bold">👨‍🌾 কৃষকের খামার ড্যাশবোর্ড</span>
              <span class="px-3 py-1 rounded-full text-xs font-bold ${user.verificationStatus === "VERIFIED" ? "bg-emerald-300 text-[#105B34]" : "bg-amber-300 text-[#105B34]"}">
                ${user.verificationStatus === "VERIFIED" ? "✓ যাচাইকৃত কৃষক" : "অ্যাডমিন যাচাই অপেক্ষমাণ"}
              </span>
            </div>
            <h1 class="text-2xl sm:text-3xl font-extrabold">স্বাগতম, ${escapeHtml(user.fullName)}!</h1>
            <p class="text-sm text-emerald-50/90">📍 ${escapeHtml(user.upazila)}, ${escapeHtml(user.district)}, ${escapeHtml(user.division)} • ০% দালাল কমিশন</p>
          </div>
          <div class="flex flex-wrap gap-2.5">
            <button onclick="window.kbApp.navigate('farmer-dashboard', { tab: 'add' })" class="px-5 py-3 rounded-2xl bg-amber-400 text-[#105B34] font-bold text-sm shadow hover:bg-amber-300">
              + নতুন ফসল যোগ করুন
            </button>
            <button onclick="window.kbApp.navigate('farmer-dashboard', { tab: 'orders' })" class="px-5 py-3 rounded-2xl bg-white text-[#105B34] font-bold text-sm shadow hover:bg-emerald-50">
              📦 প্রাপ্ত অর্ডার (${pendingOrders.length})
            </button>
            <button onclick="window.kbApp.navigate('browse')" class="px-4 py-3 rounded-2xl border border-white/40 text-white font-semibold text-sm hover:bg-white/10">
              📊 বাজার দর দেখুন
            </button>
          </div>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 border-t border-white/15">
          <div class="bg-white/10 rounded-2xl p-4"><div class="text-2xl font-extrabold">${activeMyProducts.length}</div><div class="text-xs text-emerald-100">সক্রিয় ফসল</div></div>
          <div class="bg-white/10 rounded-2xl p-4"><div class="text-2xl font-extrabold">${pendingOrders.length}</div><div class="text-xs text-emerald-100">নতুন অপেক্ষমাণ অর্ডার</div></div>
          <div class="bg-white/10 rounded-2xl p-4"><div class="text-2xl font-extrabold">${completedOrders.length}</div><div class="text-xs text-emerald-100">সম্পন্ন অর্ডার</div></div>
          <div class="bg-white/10 rounded-2xl p-4"><div class="text-2xl font-extrabold">${formatBdt(totalSales)}</div><div class="text-xs text-emerald-100">মোট বিক্রয়</div></div>
        </div>
      </section>

      <!-- Pending Buyer Orders -->
      <section class="space-y-3">
        <div class="flex items-center justify-between">
          <h2 class="text-lg sm:text-xl font-bold text-[#105B34]">📦 ক্রেতাদের নতুন অপেক্ষমাণ অর্ডার (${pendingOrders.length})</h2>
          <button onclick="window.kbApp.navigate('farmer-dashboard', { tab: 'orders' })" class="text-sm font-semibold text-[#168447] hover:underline">সব অর্ডার ব্যবস্থাপনা →</button>
        </div>
        ${
          !pendingOrders.length
            ? `<div class="bg-white rounded-3xl p-6 border border-emerald-100 text-sm text-gray-600">বর্তমানে কোনো নতুন অপেক্ষমাণ অর্ডার নেই। ক্রেতারা অর্ডার করলে তাৎক্ষণিক এখানে ও নোটিফিকেশনে পাবেন।</div>`
            : `<div class="grid md:grid-cols-2 gap-4">${pendingOrders.slice(0, 4).map((o) => `
                <div class="bg-amber-50/70 rounded-3xl p-5 border border-amber-200 shadow-sm space-y-3">
                  <div class="flex items-center justify-between">
                    <span class="font-extrabold text-[#105B34]">${escapeHtml(o.orderCode)}</span>
                    <span class="px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-200 text-amber-900">অপেক্ষমাণ</span>
                  </div>
                  <div class="text-sm font-bold">${escapeHtml(o.itemsSummary)}</div>
                  <div class="text-xs text-gray-700 flex justify-between">
                    <span>ক্রেতা: <strong>${escapeHtml(o.buyerName)}</strong> (${escapeHtml(o.upazila)})</span>
                    <strong class="text-[#168447]">${formatBdt(o.grandTotal)}</strong>
                  </div>
                  <div class="flex gap-2">
                    <button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'ACCEPTED')" class="flex-1 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">✓ গ্রহণ করুন</button>
                    <button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'REJECTED')" class="px-4 py-2 rounded-xl bg-red-600 text-white text-xs font-bold">✕ প্রত্যাখ্যান</button>
                  </div>
                </div>`).join("")}</div>`
        }
      </section>

      ${renderAdPlacement("feed")}

      <!-- Farmer Listed Inventory -->
      <section class="space-y-3">
        <div class="flex items-center justify-between">
          <h2 class="text-lg sm:text-xl font-bold text-[#105B34]">🌾 আমার তালিকাভুক্ত ফসল (${myProducts.length})</h2>
          <button onclick="window.kbApp.navigate('farmer-dashboard', { tab: 'products' })" class="text-sm font-semibold text-[#168447] hover:underline">দাম ও মজুত সম্পাদনা →</button>
        </div>
        ${
          !myProducts.length
            ? `<div class="bg-white rounded-3xl p-8 text-center border border-dashed border-emerald-200 space-y-3">
                <p class="text-sm text-gray-600">আপনার কোনো ফসল এখনো তালিকাভুক্ত করা হয়নি।</p>
                <button onclick="window.kbApp.navigate('farmer-dashboard', { tab: 'add' })" class="px-5 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">+ প্রথম ফসল যোগ করুন</button>
              </div>`
            : renderChunkedGridWithFeedAds(myProducts.slice(0, 6), renderProductCard, 2, "grid sm:grid-cols-2 lg:grid-cols-4 gap-4")
        }
      </section>

      ${renderDailyTipsCard()}

      ${renderAdPlacement("feed")}
    </div>
  `;
}

function dispatchHeroRoute(route) {
  const r = String(route || "browse").trim().toLowerCase();
  if (r === "farmers") navigate("farmers");
  else if (r === "sell") window.kbApp.goSell();
  else if (r === "activation") navigate("activation");
  else if (r === "register_farmer") navigate("auth-register-farmer");
  else navigate("browse");
}

function renderHomePage() {
  const user = getActiveUser();
  if (user && user.role === "FARMER") {
    return renderFarmerHomeWorkspace(user);
  }

  const isBuyer = user && user.role === "BUYER";
  const isAdmin = user && user.role === "ADMIN";
  const activeProducts = state.products.filter((p) => p.status === "ACTIVE");
  const farmers = state.users.filter((u) => u.role === "FARMER");
  const verifiedFarmers = farmers.filter((u) => u.verificationStatus === "VERIFIED");
  const buyers = state.users.filter((u) => u.role === "BUYER");
  const completedOrders = state.orders.filter((o) => o.status === "COMPLETED");
  const avgReviewPct = state.reviews.length
    ? Math.min(100, Math.max(0, Math.round((state.reviews.reduce((s, r) => s + Number(r.rating || 5), 0) / state.reviews.length / 5) * 100)))
    : null;
  const topCats = state.categories.filter((c) => c.isActive !== false).slice(0, 12);
  const myBuyerOrders = isBuyer ? state.orders.filter((o) => o.buyerId === user.id) : [];

  const activeSlides = state.heroSliders.filter((s) => s.isActive !== false);
  const slides = activeSlides.length
    ? activeSlides
    : [
        {
          id: "default_flagship",
          badgeBn: "১০০% দালালমুক্ত বাজার",
          titleBn: "সরাসরি কৃষকের কাছ থেকে",
          subtitleBn: "তাজা ফসল কিনুন",
          bullet1Bn: "কৃষকের ন্যায্য দাম",
          bullet2Bn: "ক্রেতার ন্যায্য দামে ক্রয়",
          bullet3Bn: "সরাসরি যোগাযোগ",
          ctaTextBn: "এখনই অর্ডার করুন",
          targetRoute: "browse",
          imageUri: ""
        }
      ];
  const slideIdx = Math.max(0, Math.min(slides.length - 1, state.heroSlideIdx || 0));
  const slide = slides[slideIdx];
  const slideImg = slide.imageUri || `${ASSET_BASE}showcase.png`;
  const bullets = [slide.bullet1Bn, slide.bullet2Bn, slide.bullet3Bn].filter(Boolean);

  const trustMetrics = [];
  if (verifiedFarmers.length > 0) trustMetrics.push({ icon: "👨‍🌾", label: "যাচাইকৃত কৃষক", value: String(verifiedFarmers.length) });
  if (buyers.length > 0) trustMetrics.push({ icon: "🛒", label: "নিবন্ধিত ক্রেতা", value: String(buyers.length) });
  if (completedOrders.length > 0) trustMetrics.push({ icon: "✓", label: "সম্পন্ন অর্ডার", value: String(completedOrders.length) });
  if (avgReviewPct !== null) trustMetrics.push({ icon: "👍", label: "সন্তুষ্টির হার", value: `${avgReviewPct}%` });

  const farmerPhoneById = new Map(farmers.map((f) => [f.id, f.phone || ""]));

  return `
    <div class="space-y-7">
      <!-- Green Search Header Strip -->
      <section class="rounded-3xl bg-gradient-to-r from-[#075B32] to-[#168447] p-4 sm:p-5 shadow-md">
        <div class="bg-white rounded-full p-1.5 pl-4 flex items-center gap-2 shadow-sm">
          <span class="text-[#168447] text-lg">🔍</span>
          <input id="home-search-input" type="text" value="${escapeHtml(state.filters.query)}"
            placeholder="কোন ফসল খুঁজছেন? (যেমন: মিনিকেট চাল, কুষ্টিয়া)..."
            onkeydown="if(event.key==='Enter') window.kbApp.applyHomeSearch()"
            class="flex-1 bg-transparent text-sm text-[#202820] focus:outline-none min-w-0" />
          <button onclick="window.kbApp.applyHomeSearch()"
            class="px-4 sm:px-5 py-2 rounded-full bg-[#168447] hover:bg-[#075B32] text-white text-xs sm:text-sm font-bold flex items-center gap-1.5 shrink-0 transition">
            <span>⚙️</span>
            <span>ফিল্টার</span>
          </button>
        </div>
      </section>

      ${
        isAdmin
          ? `<div class="bg-amber-50 border border-amber-200 rounded-3xl p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 class="font-bold text-amber-900">🛡️ সুপার অ্যাডমিন নিয়ন্ত্রণ প্যানেল</h2>
                <p class="text-xs text-amber-800">হিরো স্লাইডার, কৃষক ও ক্রেতা যাচাই, বিজ্ঞাপন ও অর্ডার পরিচালনা করুন।</p>
              </div>
              <button onclick="window.kbApp.navigate('admin')" class="px-4 py-2 rounded-xl bg-amber-600 text-white text-xs font-bold hover:bg-amber-700">অ্যাডমিন প্যানেল খুলুন →</button>
            </div>`
          : ""
      }

      ${
        isBuyer
          ? `<div class="bg-[#EAF7EF] border border-emerald-200 rounded-3xl p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3">
              <div class="space-y-0.5">
                <div class="flex items-center gap-2">
                  <span class="font-bold text-[#105B34] text-base sm:text-lg">🛍️ স্বাগতম, ${escapeHtml(user.fullName)}!</span>
                  <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold ${user.verificationStatus === "VERIFIED" ? "bg-emerald-600 text-white" : "bg-amber-200 text-amber-900"}">
                    ${user.verificationStatus === "VERIFIED" ? "✓ যাচাইকৃত ক্রেতা" : "যাচাই অপেক্ষমাণ"}
                  </span>
                </div>
                <p class="text-xs text-emerald-800">📍 ${escapeHtml(user.upazila)}, ${escapeHtml(user.district)} • সরাসরি কৃষকের কাছ থেকে বাজারমূল্যে কিনুন</p>
              </div>
              <div class="flex gap-2">
                <button onclick="window.kbApp.navigate('cart')" class="px-3.5 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">🛒 কার্ট (${state.cart.length})</button>
                <button onclick="window.kbApp.navigate('orders')" class="px-3.5 py-2 rounded-xl bg-white border border-emerald-300 text-[#105B34] text-xs font-bold">📦 আমার অর্ডার (${myBuyerOrders.length})</button>
              </div>
            </div>`
          : ""
      }

      <!-- Dynamic Hero Carousel (Admin Managed) -->
      <section class="relative overflow-hidden rounded-3xl shadow-lg min-h-[250px] bg-[#075B32] text-white">
        <img src="${escapeHtml(slideImg)}" alt="${escapeHtml(slide.titleBn)}" class="absolute inset-0 w-full h-full object-cover" onerror="this.src='${ASSET_BASE}showcase.png'" />
        <div class="absolute inset-0 bg-gradient-to-r from-[#053A1E]/95 via-[#075B32]/85 to-[#075B32]/40"></div>
        <div class="relative z-10 p-6 sm:p-9 max-w-xl space-y-3">
          ${
            slide.badgeBn
              ? `<span class="inline-block px-3 py-1 rounded-full bg-[#168447] border border-white/25 text-amber-200 text-xs font-bold">${escapeHtml(slide.badgeBn)}</span>`
              : ""
          }
          <div>
            <h1 class="text-2xl sm:text-3xl font-extrabold leading-snug text-white">${escapeHtml(slide.titleBn)}</h1>
            ${slide.subtitleBn ? `<div class="text-xl sm:text-2xl font-extrabold text-amber-300 mt-0.5">${escapeHtml(slide.subtitleBn)}</div>` : ""}
          </div>
          ${
            bullets.length
              ? `<ul class="space-y-1 text-xs sm:text-sm text-emerald-50">
                  ${bullets.map((b) => `<li class="flex items-center gap-2"><span class="text-emerald-300 font-bold">✓</span><span>${escapeHtml(b)}</span></li>`).join("")}
                </ul>`
              : ""
          }
          <div class="flex flex-wrap gap-2.5 pt-2">
            <button onclick="window.kbApp.dispatchHeroRoute('${escapeHtml(slide.targetRoute || "browse")}')"
              class="px-5 py-2.5 rounded-full bg-[#168447] hover:bg-[#105B34] border border-white text-white font-bold text-sm shadow transition">
              ${escapeHtml(slide.ctaTextBn || "এখনই অর্ডার করুন")} →
            </button>
            <button onclick="window.kbApp.navigate('farmers')"
              class="px-4 py-2.5 rounded-full bg-black/30 hover:bg-black/45 border border-white/70 text-white font-semibold text-sm">
              কৃষকদের দেখুন
            </button>
          </div>
        </div>
        ${
          slides.length > 1
            ? `<div class="relative z-10 flex items-center justify-between px-5 pb-4">
                <button type="button" onclick="window.kbApp.prevHeroSlide(${slides.length})" class="w-8 h-8 rounded-full bg-black/40 text-white text-sm font-bold hover:bg-black/60">‹</button>
                <div class="flex gap-1.5">
                  ${slides
                    .map((_, i) => `<button type="button" onclick="window.kbApp.setHeroSlide(${i})" class="h-2 rounded-full transition-all ${i === slideIdx ? "w-6 bg-amber-300" : "w-2 bg-white/50"}"></button>`)
                    .join("")}
                </div>
                <button type="button" onclick="window.kbApp.nextHeroSlide(${slides.length})" class="w-8 h-8 rounded-full bg-black/40 text-white text-sm font-bold hover:bg-black/60">›</button>
              </div>`
            : ""
        }
      </section>

      <!-- Circular Categories Strip -->
      <section class="space-y-3">
        <div class="flex items-center justify-between">
          <h2 class="text-lg sm:text-xl font-bold text-[#075B32]">ফসলের ক্যাটাগরি</h2>
          <button onclick="window.kbApp.navigate('browse')" class="text-sm font-semibold text-[#168447] hover:underline">সব দেখুন →</button>
        </div>
        <div class="flex gap-3 overflow-x-auto pb-2">
          ${topCats
            .map(
              (c) => `
            <button onclick="window.kbApp.filterByCategory('${escapeHtml(c.id)}')"
              class="shrink-0 w-20 flex flex-col items-center gap-1.5 group">
              <div class="w-16 h-16 rounded-full bg-[#EAF7EF] border-2 border-[#168447]/30 group-hover:border-[#168447] flex items-center justify-center text-3xl shadow-sm transition">
                ${escapeHtml(c.emoji || "🌱")}
              </div>
              <div class="text-xs font-bold text-[#202820] text-center truncate w-full">${escapeHtml(c.nameBn)}</div>
            </button>
          `
            )
            .join("")}
        </div>
      </section>

      <!-- Today's Fresh Crops -->
      <section class="space-y-4">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-xl">🌱</span>
            <h2 class="text-lg sm:text-xl font-extrabold text-[#075B32]">আজকের নতুন ফসল</h2>
          </div>
          <button onclick="window.kbApp.navigate('browse')" class="px-3.5 py-1.5 rounded-full bg-[#EAF7EF] border border-emerald-200 text-xs font-bold text-[#075B32] hover:bg-emerald-100">সব দেখুন →</button>
        </div>

        ${
          activeProducts.length === 0
            ? `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200 space-y-3">
                <div class="text-4xl">🌱</div>
                <h3 class="text-base font-bold text-[#105B34]">এখনো কোনো ফসল তালিকাভুক্ত হয়নি</h3>
                ${
                  isBuyer
                    ? `<p class="text-sm text-gray-600 max-w-md mx-auto">কৃষকরা নতুন ফসল প্রকাশ করলে এখানে দেখাবে। আপনি যাচাইকৃত কৃষক তালিকা দেখতে পারেন।</p>
                       <button onclick="window.kbApp.navigate('farmers')" class="px-5 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">কৃষকদের দেখুন</button>`
                    : `<p class="text-sm text-gray-600 max-w-md mx-auto">কোনো ডেমো ডেটা রাখা হয়নি। কৃষক হিসেবে নিবন্ধন করে প্রথম তাজা ফসল যোগ করুন!</p>
                       <button onclick="window.kbApp.goSell()" class="px-5 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">ফসল যোগ করুন</button>`
                }
              </div>`
            : `<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                ${activeProducts.slice(0, 8).map((p) => {
                  const cat = state.categories.find((c) => c.id === p.categoryId);
                  const emoji = cat?.emoji || "🌾";
                  const phone = String(farmerPhoneById.get(p.farmerId) || "").replace(/[^\d+]/g, "");
                  return `
                    <div class="bg-white rounded-2xl border border-emerald-100 shadow-sm hover:shadow-md transition overflow-hidden flex flex-col">
                      <button onclick="window.kbApp.navigate('product-detail', { id: '${escapeHtml(p.id)}' })" class="relative block w-full text-left">
                        ${
                          p.imageUri
                            ? `<img src="${escapeHtml(p.imageUri)}" alt="${escapeHtml(p.nameBn)}" class="w-full h-36 object-cover" onerror="this.style.display='none'" />`
                            : `<div class="w-full h-36 bg-[#EAF7EF] flex items-center justify-center text-4xl">${emoji}</div>`
                        }
                        <span class="absolute top-2.5 right-2.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-[#168447] text-white shadow">তাজা</span>
                        ${p.farmerVerified ? `<span class="absolute top-2.5 left-2.5 px-2 py-0.5 rounded-full text-[10px] font-bold bg-white/95 text-[#075B32] shadow">✓ যাচাইকৃত</span>` : ""}
                      </button>
                      <div class="p-3.5 flex-1 flex flex-col justify-between gap-2.5">
                        <div class="space-y-1">
                          <h3 class="font-bold text-sm text-[#202820] line-clamp-1">${escapeHtml(p.nameBn)}</h3>
                          <div class="text-base font-extrabold text-[#168447]">${formatBdt(p.pricePerUnit)}<span class="text-xs font-medium text-gray-500"> / মণ</span></div>
                          <div class="text-[11px] text-gray-600 truncate">📍 ${escapeHtml(p.upazila || "")}, ${escapeHtml(p.district || "")}</div>
                          <div class="text-[11px] text-[#075B32] font-medium">মজুত: ${formatQty(p.availableQuantity)} মণ</div>
                        </div>
                        <div class="flex gap-2">
                          ${
                            phone
                              ? `<a href="tel:${escapeHtml(phone)}" class="flex-1 py-2 rounded-xl bg-[#168447] hover:bg-[#075B32] text-white text-xs font-bold text-center">📞 কল করুন</a>`
                              : `<button onclick="window.kbApp.navigate('product-detail', { id: '${escapeHtml(p.id)}' })" class="flex-1 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">বিস্তারিত</button>`
                          }
                          <button onclick="window.kbApp.addToCart('${escapeHtml(p.id)}', 1)" class="px-3 py-2 rounded-xl bg-[#EAF7EF] text-[#075B32] border border-emerald-200 text-xs font-bold" title="কার্টে যোগ করুন">🛒</button>
                        </div>
                      </div>
                    </div>
                  `;
                }).join("")}
              </div>`
        }
      </section>

      ${
        trustMetrics.length
          ? `<section class="bg-[#EAF7EF] rounded-3xl p-4 sm:p-5 border border-emerald-200 grid grid-cols-2 sm:grid-cols-${Math.min(4, trustMetrics.length)} gap-3 text-center">
              ${trustMetrics
                .map(
                  (m) => `<div class="bg-white/70 rounded-2xl p-3 flex items-center justify-center gap-2.5">
                    <span class="text-xl">${m.icon}</span>
                    <div class="text-left">
                      <div class="text-[11px] text-gray-600 font-semibold">${escapeHtml(m.label)}</div>
                      <div class="text-base font-extrabold text-[#075B32]">${escapeHtml(m.value)}</div>
                    </div>
                  </div>`
                )
                .join("")}
            </section>`
          : ""
      }

      <!-- Promotional Dark-Green Farmer Registration Banner -->
      <section class="rounded-3xl bg-gradient-to-r from-[#075B32] to-[#168447] text-white p-5 sm:p-6 shadow-md flex flex-col sm:flex-row items-center justify-between gap-4">
        <div class="flex items-center gap-4">
          <img src="${ASSET_BASE}showcase.png" alt="কৃষি বন্ধু" class="w-24 h-16 rounded-2xl object-cover border border-white/25 shrink-0" />
          <div>
            <div class="text-xs font-semibold text-emerald-100">কৃষি বন্ধুর সাথে থাকুন</div>
            <h3 class="text-lg sm:text-xl font-extrabold text-amber-300">কৃষি হোক আরও সমৃদ্ধ</h3>
          </div>
        </div>
        <button onclick="${user ? "window.kbApp.goSell()" : "window.kbApp.navigate('auth-register-farmer')"}"
          class="px-5 py-2.5 rounded-full bg-amber-400 hover:bg-amber-300 text-[#075B32] font-extrabold text-sm shadow shrink-0 transition">
          এখনই যোগ দিন →
        </button>
      </section>

      ${renderAdPlacement("feed")}

      ${
        farmers.length
          ? `<section class="space-y-4">
              <div class="flex items-center justify-between">
                <div>
                  <h2 class="text-lg sm:text-xl font-bold text-[#105B34]">👨‍🌾 যাচাইকৃত কৃষক (${verifiedFarmers.length})</h2>
                  <p class="text-xs text-gray-500">সরাসরি খামার থেকে পাইকারি ফসল সংগ্রহ করুন</p>
                </div>
                <button onclick="window.kbApp.navigate('farmers')" class="text-sm font-semibold text-[#168447] hover:underline">সব কৃষক দেখুন →</button>
              </div>
              ${renderChunkedGridWithFeedAds(
                (verifiedFarmers.length ? verifiedFarmers : farmers).slice(0, 6),
                (u) => {
                  const cnt = state.products.filter((p) => p.farmerId === u.id && p.status === "ACTIVE").length;
                  return `<div class="bg-white rounded-3xl p-4 border border-emerald-100 shadow-sm space-y-2.5">
                    <div class="flex items-center gap-2.5">
                      ${u.profileImageUri ? `<img src="${escapeHtml(u.profileImageUri)}" alt="" class="w-10 h-10 rounded-full object-cover border border-emerald-200" />` : `<div class="w-10 h-10 rounded-full bg-[#EAF7EF] flex items-center justify-center">👨‍🌾</div>`}
                      <div class="min-w-0 flex-1">
                        <div class="font-bold text-sm text-[#105B34] truncate">${escapeHtml(u.fullName)}</div>
                        <div class="text-[11px] text-gray-600 truncate">📍 ${escapeHtml(u.upazila)}, ${escapeHtml(u.district)}</div>
                      </div>
                    </div>
                    <div class="flex items-center justify-between text-xs text-gray-600">
                      <span>★ ${Number(u.rating || 5).toFixed(1)}</span>
                      <span>${cnt}টি ফসল</span>
                    </div>
                    <button onclick="window.kbApp.navigate('farmer-profile', { id: '${escapeHtml(u.id)}' })" class="w-full py-2 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold hover:bg-emerald-100">খামার দেখুন</button>
                  </div>`;
                },
                3,
                "grid sm:grid-cols-2 lg:grid-cols-3 gap-4"
              )}
            </section>`
          : ""
      }

      ${renderDailyTipsCard()}

      ${renderAdPlacement("feed")}
    </div>
  `;
}

function renderAccountStatusBanner(user) {
  if (!user || user.role === "ADMIN") return "";
  const activeOk = isMarketplaceActive(user);
  const st = user.accountStatus || "PAYMENT_PENDING";
  const stMeta = ACCOUNT_STATUS_META[st] || ACCOUNT_STATUS_META.PAYMENT_PENDING;
  const daysLeft = daysUntilExpiry(user);

  if (activeOk) {
    if (daysLeft !== null && daysLeft <= 30) {
      return `
        <div class="bg-amber-50 border border-amber-300 rounded-3xl p-4 flex flex-wrap items-center justify-between gap-3">
          <div class="text-xs sm:text-sm text-amber-900">
            <strong>⏳ অ্যাকাউন্ট মেয়াদ শেষ হতে ${daysLeft} দিন বাকি</strong> (মেয়াদ: ${formatDateBn(user.expiryDate)})। নিরবচ্ছিন্ন সেবার জন্য ৳${FIXED_ACTIVATION_FEE_BDT} দিয়ে নবায়ন করুন।
          </div>
          <button onclick="window.kbApp.navigate('activation')" class="px-4 py-2 rounded-xl bg-amber-600 text-white text-xs font-bold">নবায়ন করুন</button>
        </div>
      `;
    }
    return "";
  }

  return `
    <div class="bg-red-50 border border-red-200 rounded-3xl p-4 flex flex-wrap items-center justify-between gap-3">
      <div class="space-y-0.5">
        <div class="flex items-center gap-2">
          <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold ${stMeta.color}">${stMeta.labelBn}</span>
          <span class="font-bold text-sm text-red-900">মার্কেটপ্লেস ব্যবহারের জন্য অ্যাকাউন্ট সক্রিয় করা প্রয়োজন</span>
        </div>
        <p class="text-xs text-red-800">
          ${st === "PAYMENT_UNDER_REVIEW"
            ? "আপনার ৳৩০০ পেমেন্ট অ্যাডমিন যাচাইয়ের অধীনে আছে। অনুমোদন হলে ২ বছরের জন্য সব সুবিধা চালু হবে।"
            : st === "SUSPENDED"
            ? "আপনার অ্যাকাউন্ট স্থগিত রয়েছে। প্রোফাইল ও নোটিফিকেশন দেখুন।"
            : `৳${FIXED_ACTIVATION_FEE_BDT} এককালীন ফি-তে ${FIXED_VALIDITY_YEARS} বছর মেয়াদ পাবেন। এখনই পেমেন্ট জমা দিন।`}
        </p>
      </div>
      <div class="flex gap-2">
        <button onclick="window.kbApp.navigate('activation')" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">⚡ অ্যাক্টিভেশন পাতা</button>
        <button onclick="window.kbApp.navigate('profile')" class="px-3 py-2 rounded-xl bg-white border border-red-200 text-red-800 text-xs font-bold">প্রোফাইল</button>
      </div>
    </div>
  `;
}

function renderBrowsePage() {
  const f = state.filters;
  const user = getActiveUser();
  const distOptions = f.division ? BD_DISTRICTS.filter((d) => normBn(d.divisionBn) === normBn(f.division)) : BD_DISTRICTS;
  const upzOptions = getUpazilaOptions(f.division, f.district);
  const unionOptions = getUnionOptions(f.district, f.upazila, f.division);
  const filtered = getFilteredProducts();

  return `
    <div class="space-y-6">
      ${renderAccountStatusBanner(user)}
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <h1 class="text-2xl font-bold text-[#105B34]">🌾 সারা বাংলাদেশের ফসল খুঁজুন (মণ হিসেবে)</h1>
          <p class="text-xs text-gray-600">মোট ${filtered.length}টি সক্রিয় ফসল পাওয়া গেছে • ১ মণ = ${MON_TO_KG} কেজি</p>
        </div>
        <div class="flex flex-wrap gap-2">
          ${user && user.district ? `<button onclick="window.kbApp.applyMyAreaFilter()" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">🏡 আমার এলাকা (${escapeHtml(user.upazila || user.district)})</button>` : ""}
          <button onclick="window.kbApp.detectBrowserLocation('browse')" class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-xs font-bold text-[#105B34] hover:bg-emerald-100">
            📍 GPS অটো
          </button>
          <button onclick="window.kbApp.openLocationMapModal('browse')" class="px-3 py-1.5 rounded-xl bg-white border border-emerald-200 text-xs font-bold text-[#105B34] hover:bg-[#EAF7EF]">
            🗺️ ম্যাপ
          </button>
          <button onclick="window.kbApp.resetFilters()" class="px-3 py-1.5 rounded-xl border border-emerald-200 text-xs font-semibold text-[#105B34] hover:bg-[#EAF7EF]">
            রিসেট
          </button>
        </div>
      </div>

      <!-- Cascading Dropdown Filters (4-level Location + Category/Type/Sort) -->
      <div class="bg-white rounded-3xl p-4 sm:p-5 border border-emerald-100 shadow-sm grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <input id="browse-query" type="text" value="${escapeHtml(f.query)}" oninput="window.kbApp.setFilter('query', this.value)"
          placeholder="ফসল, এলাকা বা কৃষক খুঁজুন..." class="px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />

        <select onchange="window.kbApp.setFilter('division', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="">সব বিভাগ (৮টি)</option>
          ${BD_DIVISIONS.map((d) => `<option value="${escapeHtml(d)}" ${normBn(f.division) === normBn(d) ? "selected" : ""}>${escapeHtml(d)} বিভাগ</option>`).join("")}
        </select>

        <select onchange="window.kbApp.setFilter('district', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="">সব জেলা (৬৪টি)</option>
          ${distOptions.map((d) => `<option value="${escapeHtml(d.districtBn)}" ${normBn(f.district) === normBn(d.districtBn) ? "selected" : ""}>${escapeHtml(d.districtBn)}</option>`).join("")}
        </select>

        <select onchange="window.kbApp.setFilter('upazila', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white" ${!upzOptions.length ? "disabled" : ""}>
          <option value="">সব উপজেলা</option>
          ${upzOptions.map((u) => `<option value="${escapeHtml(u)}" ${normBn(f.upazila) === normBn(u) ? "selected" : ""}>${escapeHtml(u)}</option>`).join("")}
        </select>

        <select onchange="window.kbApp.setFilter('union', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white" ${!unionOptions.length ? "disabled" : ""}>
          <option value="">সব ইউনিয়ন</option>
          ${unionOptions.map((un) => `<option value="${escapeHtml(un)}" ${normBn(f.union) === normBn(un) ? "selected" : ""}>${escapeHtml(un)}</option>`).join("")}
        </select>

        <select onchange="window.kbApp.setFilter('categoryId', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="">সব ক্যাটাগরি</option>
          ${state.categories.map((c) => `<option value="${escapeHtml(c.id)}" ${f.categoryId === c.id ? "selected" : ""}>${escapeHtml(c.emoji || "")} ${escapeHtml(c.nameBn)}</option>`).join("")}
        </select>

        <div class="px-3 py-2.5 rounded-xl border border-amber-200 bg-amber-50 text-amber-900 text-xs font-bold flex items-center">
          📦 পাইকারি বাজার মাত্র (খুচরা বন্ধ)
        </div>

        <select onchange="window.kbApp.setFilter('sort', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="NEWEST" ${f.sort === "NEWEST" ? "selected" : ""}>নতুন আগে</option>
          <option value="PRICE_LOW_HIGH" ${f.sort === "PRICE_LOW_HIGH" ? "selected" : ""}>কম দাম আগে</option>
          <option value="PRICE_HIGH_LOW" ${f.sort === "PRICE_HIGH_LOW" ? "selected" : ""}>বেশি দাম আগে</option>
        </select>
      </div>

      ${
        filtered.length === 0
          ? `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200">
              <div class="text-4xl mb-2">🔍</div>
              <p class="font-bold text-[#105B34]">এই ফিল্টারে কোনো ফসল পাওয়া যায়নি</p>
              <p class="text-xs text-gray-500 mt-1">অন্য জেলা, উপজেলা বা ক্যাটাগরি নির্বাচন করে চেষ্টা করুন।</p>
            </div>`
          : renderChunkedGridWithFeedAds(filtered, renderProductCard, 4, "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4")
      }
      ${renderAdPlacement("feed")}
    </div>
  `;
}

function renderProductDetailPage() {
  const prod = state.products.find((p) => p.id === state.pageParams.id);
  if (!prod) {
    return `<div class="bg-white rounded-3xl p-8 text-center"><p>ফসলটি পাওয়া যায়নি।</p><button onclick="window.kbApp.navigate('browse')" class="mt-3 px-4 py-2 rounded-xl bg-[#168447] text-white text-sm">ফিরে যান</button></div>`;
  }
  const viewer = getActiveUser();
  const canViewUnpublished = Boolean(viewer && (viewer.role === "ADMIN" || viewer.id === prod.farmerId));
  if ((prod.status === "PENDING_APPROVAL" || prod.status === "REJECTED") && !canViewUnpublished) {
    return `
      <div class="bg-white rounded-3xl p-8 text-center border border-amber-200 max-w-lg mx-auto space-y-3">
        <div class="text-4xl">⏳</div>
        <h2 class="text-lg font-bold text-[#105B34]">এই ফসলটি এখনো অ্যাডমিন যাচাইয়ের অধীনে রয়েছে</h2>
        <p class="text-sm text-gray-600">কৃষকের যুক্ত করা নতুন ফসল অ্যাডমিন অনুমোদন করলেই সবার জন্য প্রকাশিত হয়।</p>
        <button onclick="window.kbApp.navigate('browse')" class="px-5 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">সকল অনুমোদিত ফসল দেখুন</button>
      </div>
    `;
  }
  const viewerActive = viewer && isMarketplaceActive(viewer);
  const farmer = state.users.find((u) => u.id === prod.farmerId);
  const maxQ = Math.max(0.5, Number(prod.availableQuantity || 0));
  const initialQ = Math.min(1, maxQ);
  const stockKg = formatQty(Number(prod.availableQuantity || 0) * MON_TO_KG);

  return `
    <div class="space-y-6">
      <button onclick="window.kbApp.navigate('browse')" class="text-sm font-semibold text-[#168447] hover:underline">← সকল ফসলে ফিরে যান</button>
      <div class="bg-white rounded-3xl border border-emerald-100 shadow-sm overflow-hidden grid md:grid-cols-2">
        <div class="bg-[#EAF7EF] flex items-center justify-center min-h-[280px]">
          ${
            prod.imageUri
              ? `<img src="${escapeHtml(prod.imageUri)}" alt="${escapeHtml(prod.nameBn)}" class="w-full h-full max-h-[420px] object-cover" />`
              : `<div class="text-7xl py-16">🌾</div>`
          }
        </div>
        <div class="p-6 sm:p-8 space-y-4">
          <div class="flex flex-wrap items-center gap-2">
            <span class="px-3 py-1 rounded-full bg-[#EAF7EF] text-[#105B34] text-xs font-bold">${escapeHtml(prod.categoryNameBn)}</span>
            <span class="px-3 py-1 rounded-full bg-emerald-50 border border-emerald-200 text-[#105B34] text-xs font-bold">১ মণ = ${MON_TO_KG} কেজি</span>
            <span class="px-3 py-1 rounded-full bg-amber-100 text-amber-800 text-xs font-bold">পাইকারি মাত্র</span>
            ${prod.farmerVerified ? `<span class="px-3 py-1 rounded-full bg-emerald-600 text-white text-xs font-bold">✓ যাচাইকৃত কৃষক</span>` : ""}
          </div>
          <h1 class="text-2xl sm:text-3xl font-extrabold text-[#202820]">${escapeHtml(prod.nameBn)}</h1>
          <div class="text-2xl font-extrabold text-[#168447]">${formatBdt(prod.pricePerUnit)} <span class="text-sm font-medium text-gray-500">/ মণ (${MON_TO_KG} কেজি)</span></div>
          <p class="text-sm text-gray-700 leading-relaxed">${escapeHtml(prod.descriptionBn || "সরাসরি কৃষকের খামার থেকে সংগ্রহ করা তাজা কৃষিপণ্য।")}</p>

          <div class="grid grid-cols-2 gap-3 bg-[#F7FAF7] p-4 rounded-2xl text-xs">
            <div><span class="text-gray-500 block">উপলব্ধ মজুত</span><strong class="text-sm">${formatQty(prod.availableQuantity)} মণ (${stockKg} কেজি)</strong></div>
            <div><span class="text-gray-500 block">এলাকা</span><strong class="text-sm">${escapeHtml(prod.unionName ? prod.unionName + ", " : "")}${escapeHtml(prod.upazila)}, ${escapeHtml(prod.district)}</strong></div>
            <div><span class="text-gray-500 block">ফসল তোলার তারিখ</span><strong>${escapeHtml(prod.harvestDate || "আজ")}</strong></div>
            <div><span class="text-gray-500 block">সংগ্রহ ও ডেলিভারি</span><strong>${prod.supportsDirectDelivery ? `হোম ডেলিভারি (${formatBdt(prod.deliveryChargeEstimate)})` : "খামার থেকে সংগ্রহ"}</strong></div>
          </div>

          ${
            (() => {
              if (viewer && viewer.role === "FARMER" && viewer.id === prod.farmerId) {
                return `<div class="bg-[#EAF7EF] border border-emerald-200 rounded-2xl p-4 text-xs sm:text-sm font-bold text-[#105B34]">
                  ✓ এটি আপনার নিজস্ব তালিকাভুক্ত ফসল। <button onclick="window.kbApp.navigate('farmer-dashboard', { tab: 'products' })" class="underline ml-1">দাম বা মজুত পরিবর্তন করুন →</button>
                </div>`;
              }
              if (viewer && viewer.role === "FARMER") {
                return `<div class="bg-gray-50 border border-gray-200 rounded-2xl p-3.5 text-xs text-gray-700 font-semibold">
                  📊 আপনি কৃষক অ্যাকাউন্ট থেকে বাজার দর পর্যবেক্ষণ করছেন।
                </div>`;
              }
              return `<div class="space-y-2 pt-2">
                <div class="flex items-center gap-3">
                  <div class="flex items-center gap-1.5">
                    <input id="detail-qty" type="number" min="0.5" max="${maxQ}" step="0.5" value="${initialQ}"
                      oninput="const k=document.getElementById('detail-kg-preview'); if(k) k.textContent = (Number(this.value||0)*${MON_TO_KG}).toFixed(0)+' কেজি • ৳'+(Number(this.value||0)*${Number(prod.pricePerUnit||0)}).toLocaleString('en-IN');"
                      class="w-24 px-3 py-3 rounded-xl border border-emerald-200 text-center font-bold" />
                    <span class="text-xs font-bold text-[#105B34]">মণ</span>
                  </div>
                  <button onclick="window.kbApp.addToCart('${escapeHtml(prod.id)}', document.getElementById('detail-qty').value)"
                    class="flex-1 py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">
                    🛒 কার্টে যোগ করুন
                  </button>
                </div>
                <div id="detail-kg-preview" class="text-xs text-emerald-800 font-semibold">${formatQty(initialQ * MON_TO_KG)} কেজি • ${formatBdt(initialQ * prod.pricePerUnit)}</div>
              </div>`;
            })()
          }

          <!-- Farmer card -->
          <div class="border-t border-emerald-100 pt-4 flex items-center justify-between gap-3">
            <div class="flex items-center gap-2.5">
              ${farmer?.profileImageUri ? `<img src="${escapeHtml(farmer.profileImageUri)}" alt="" class="w-10 h-10 rounded-full object-cover border border-emerald-200" />` : `<div class="w-10 h-10 rounded-full bg-[#EAF7EF] flex items-center justify-center">👨‍🌾</div>`}
              <div>
                <div class="font-bold text-sm text-[#105B34]">${escapeHtml(prod.farmerName)}</div>
                <div class="text-xs text-gray-600">${escapeHtml(prod.pickupAddress || prod.district)} • ★ ${(farmer?.rating || 5).toFixed(1)} (${farmer?.reviewCount || 0} রিভিউ)</div>
              </div>
            </div>
            <div class="flex gap-2">
              ${
                farmer?.phone && viewerActive
                  ? `<a href="tel:${escapeHtml(farmer.phone)}" class="px-3 py-2 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold">📞 কল</a>`
                  : `<button onclick="window.kbApp.requireContactGate()" class="px-3 py-2 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs font-bold">🔒 কল (সক্রিয় করুন)</button>`
              }
              <button onclick="window.kbApp.navigate('farmer-profile', { id: '${escapeHtml(prod.farmerId)}' })" class="px-3 py-2 rounded-xl border border-emerald-200 text-xs font-semibold">প্রোফাইল</button>
            </div>
          </div>
        </div>
      </div>
      ${renderAdPlacement("feed")}
    </div>
  `;
}

function renderFarmersPage() {
  const f = state.filters;
  const user = getActiveUser();
  const distOptions = f.division ? BD_DISTRICTS.filter((d) => normBn(d.divisionBn) === normBn(f.division)) : BD_DISTRICTS;
  const upzOptions = getUpazilaOptions(f.division, f.district);
  const unionOptions = getUnionOptions(f.district, f.upazila, f.division);
  const farmers = getFilteredFarmers();
  const viewerActive = user && isMarketplaceActive(user);

  return `
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 class="text-2xl font-bold text-[#105B34]">👨‍🌾 বাংলাদেশের কৃষকদের খুঁজুন</h1>
          <p class="text-xs text-gray-600">সরাসরি কৃষকের সাথে কথা বলে ফসল কিনুন — কোনো দালাল নেই</p>
        </div>
        <div class="flex flex-wrap gap-2">
          ${user && user.district ? `<button onclick="window.kbApp.applyMyAreaFilter()" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">🏡 আমার এলাকা</button>` : ""}
          <button onclick="window.kbApp.detectBrowserLocation('farmers')" class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-xs font-bold text-[#105B34]">📍 GPS অটো</button>
          <button onclick="window.kbApp.openLocationMapModal('farmers')" class="px-3 py-1.5 rounded-xl bg-white border border-emerald-200 text-xs font-bold text-[#105B34]">🗺️ ম্যাপ</button>
          <button onclick="window.kbApp.resetFilters()" class="px-3 py-1.5 rounded-xl border border-emerald-200 text-xs font-semibold text-[#105B34]">রিসেট</button>
        </div>
      </div>

      <div class="bg-white rounded-3xl p-4 border border-emerald-100 shadow-sm grid sm:grid-cols-2 lg:grid-cols-5 gap-3">
        <input type="text" value="${escapeHtml(f.query)}" oninput="window.kbApp.setFilter('query', this.value)" placeholder="কৃষকের নাম বা এলাকা খুঁজুন..."
          class="px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
        <select onchange="window.kbApp.setFilter('division', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="">সব বিভাগ</option>
          ${BD_DIVISIONS.map((d) => `<option value="${escapeHtml(d)}" ${normBn(f.division) === normBn(d) ? "selected" : ""}>${escapeHtml(d)}</option>`).join("")}
        </select>
        <select onchange="window.kbApp.setFilter('district', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="">সব জেলা</option>
          ${distOptions.map((d) => `<option value="${escapeHtml(d.districtBn)}" ${normBn(f.district) === normBn(d.districtBn) ? "selected" : ""}>${escapeHtml(d.districtBn)}</option>`).join("")}
        </select>
        <select onchange="window.kbApp.setFilter('upazila', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white" ${!upzOptions.length ? "disabled" : ""}>
          <option value="">সব উপজেলা</option>
          ${upzOptions.map((u) => `<option value="${escapeHtml(u)}" ${normBn(f.upazila) === normBn(u) ? "selected" : ""}>${escapeHtml(u)}</option>`).join("")}
        </select>
        <select onchange="window.kbApp.setFilter('union', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white" ${!unionOptions.length ? "disabled" : ""}>
          <option value="">সব ইউনিয়ন</option>
          ${unionOptions.map((un) => `<option value="${escapeHtml(un)}" ${normBn(f.union) === normBn(un) ? "selected" : ""}>${escapeHtml(un)}</option>`).join("")}
        </select>
      </div>

      ${
        farmers.length === 0
          ? `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200">এখনো কোনো কৃষক পাওয়া যায়নি।</div>`
          : renderChunkedGridWithFeedAds(
              farmers,
              (u) => {
                const count = state.products.filter((p) => p.farmerId === u.id && p.status === "ACTIVE").length;
                return `
                  <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
                    <div class="flex items-start justify-between gap-2">
                      <div class="flex items-center gap-2.5">
                        ${u.profileImageUri ? `<img src="${escapeHtml(u.profileImageUri)}" alt="" class="w-10 h-10 rounded-full object-cover border border-emerald-200" />` : `<div class="w-10 h-10 rounded-full bg-[#EAF7EF] flex items-center justify-center">👨‍🌾</div>`}
                        <div>
                          <h3 class="font-bold text-base text-[#105B34]">${escapeHtml(u.fullName)}</h3>
                          <p class="text-xs text-gray-600">📍 ${escapeHtml(u.unionName ? u.unionName + ", " : "")}${escapeHtml(u.upazila)}, ${escapeHtml(u.district)} (${escapeHtml(u.division)})</p>
                        </div>
                      </div>
                      <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold ${u.verificationStatus === "VERIFIED" ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-800"}">
                        ${u.verificationStatus === "VERIFIED" ? "✓ যাচাইকৃত" : "যাচাই চলমান"}
                      </span>
                    </div>
                    <p class="text-xs text-gray-700 line-clamp-2">${escapeHtml(u.farmBio || "সরাসরি ফসল উৎপাদনকারী কৃষক")}</p>
                    <div class="flex items-center justify-between text-xs text-gray-600 pt-1">
                      <span>★ ${Number(u.rating || 5).toFixed(1)} (${u.reviewCount || 0} রিভিউ)</span>
                      <span>${count}টি সক্রিয় ফসল</span>
                    </div>
                    <div class="flex gap-2 pt-1">
                      <button onclick="window.kbApp.navigate('farmer-profile', { id: '${escapeHtml(u.id)}' })" class="flex-1 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">খামার দেখুন</button>
                      ${
                        viewerActive
                          ? `<a href="tel:${escapeHtml(u.phone)}" class="px-3 py-2 rounded-xl border border-emerald-200 text-xs font-bold text-[#105B34]">📞 কল</a>`
                          : `<button onclick="window.kbApp.requireContactGate()" class="px-3 py-2 rounded-xl border border-amber-200 bg-amber-50 text-xs font-bold text-amber-900">🔒 কল</button>`
                      }
                    </div>
                  </div>
                `;
              },
              3,
              "grid sm:grid-cols-2 lg:grid-cols-3 gap-4"
            )
      }
      ${renderAdPlacement("feed")}
    </div>
  `;
}

function renderFarmerPublicProfilePage() {
  const farmer = state.users.find((u) => u.id === state.pageParams.id);
  if (!farmer) return `<div class="bg-white rounded-3xl p-8 text-center">কৃষক পাওয়া যায়নি।</div>`;
  const prods = state.products.filter((p) => p.farmerId === farmer.id && p.status === "ACTIVE");
  const revs = state.reviews.filter((r) => r.farmerId === farmer.id);
  const viewer = getActiveUser();
  const viewerActive = viewer && isMarketplaceActive(viewer);

  return `
    <div class="space-y-6">
      <button onclick="window.kbApp.navigate('farmers')" class="text-sm font-semibold text-[#168447] hover:underline">← কৃষক তালিকায় ফিরুন</button>
      <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div class="space-y-1">
          <div class="flex items-center gap-2">
            ${farmer.profileImageUri ? `<img src="${escapeHtml(farmer.profileImageUri)}" alt="" class="w-12 h-12 rounded-full object-cover border border-emerald-200" />` : ""}
            <h1 class="text-2xl font-bold text-[#105B34]">${escapeHtml(farmer.fullName)}</h1>
            ${farmer.verificationStatus === "VERIFIED" ? `<span class="px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-xs font-bold">✓ যাচাইকৃত কৃষক</span>` : ""}
          </div>
          <p class="text-sm text-gray-600">📍 ${escapeHtml(farmer.village ? farmer.village + ", " : "")}${escapeHtml(farmer.unionName ? farmer.unionName + ", " : "")}${escapeHtml(farmer.upazila)}, ${escapeHtml(farmer.district)}, ${escapeHtml(farmer.division)}</p>
          <p class="text-sm text-gray-700">${escapeHtml(farmer.farmBio || "")}</p>
          <p class="text-xs font-bold text-amber-700">★ ${Number(farmer.rating || 5).toFixed(1)} (${farmer.reviewCount || 0} রিভিউ)</p>
        </div>
        <div class="flex gap-2">
          ${
            viewerActive
              ? `<a href="tel:${escapeHtml(farmer.phone)}" class="px-4 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">📞 কল: ${escapeHtml(farmer.phone)}</a>
                 <a href="sms:${escapeHtml(farmer.phone)}" class="px-4 py-2.5 rounded-xl border border-emerald-200 text-[#105B34] text-sm font-bold">💬 SMS</a>`
              : `<button onclick="window.kbApp.requireContactGate()" class="px-4 py-2.5 rounded-xl bg-amber-50 border border-amber-300 text-amber-900 text-sm font-bold">🔒 যোগাযোগ করতে সক্রিয় করুন</button>`
          }
        </div>
      </div>

      <h2 class="text-lg font-bold text-[#105B34]">কৃষকের ফসলসমূহ (${prods.length})</h2>
      <div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
        ${prods.length ? prods.map(renderProductCard).join("") : `<p class="text-sm text-gray-500">বর্তমানে কোনো সক্রিয় ফসল নেই।</p>`}
      </div>

      <h2 class="text-lg font-bold text-[#105B34]">ক্রেতাদের রিভিউ (${revs.length})</h2>
      <div class="grid sm:grid-cols-2 gap-3">
        ${
          revs.length
            ? revs
                .map(
                  (r) => `<div class="bg-white rounded-2xl p-4 border border-emerald-100">
                    <div class="flex justify-between text-xs font-bold text-[#105B34]"><span>${escapeHtml(r.buyerName)}</span><span>★ ${r.rating}/5</span></div>
                    <p class="text-sm text-gray-700 mt-1">${escapeHtml(r.commentBn)}</p>
                  </div>`
                )
                .join("")
            : `<p class="text-sm text-gray-500">এখনো কোনো রিভিউ দেওয়া হয়নি।</p>`
        }
      </div>
    </div>
  `;
}

function renderLoginPage() {
  return `
    <div class="max-w-md mx-auto bg-white rounded-3xl p-6 sm:p-8 border border-emerald-100 shadow-sm space-y-5">
      <div class="text-center space-y-1">
        <img src="${ASSET_BASE}logo.png" alt="কৃষি বন্ধু" class="w-16 h-16 mx-auto rounded-2xl border border-emerald-100 shadow-sm" />
        <h1 class="text-2xl font-bold text-[#105B34]">অ্যাকাউন্টে লগইন</h1>
        <p class="text-xs text-gray-600">আপনার মোবাইল নম্বর অথবা অ্যাডমিন আইডি দিয়ে প্রবেশ করুন</p>
      </div>
      <form onsubmit="window.kbApp.handleLoginSubmit(event)" class="space-y-4">
        <div>
          <label class="text-xs font-bold text-gray-700 block mb-1">মোবাইল নম্বর / ইউজার আইডি</label>
          <input name="identifier" required placeholder="017XXXXXXXX" class="w-full px-4 py-3 rounded-xl border border-emerald-200 text-sm" />
        </div>
        <div>
          <label class="text-xs font-bold text-gray-700 block mb-1">পাসওয়ার্ড</label>
          <div class="relative">
            <input id="login-password-input" name="password" type="password" required placeholder="******" class="w-full px-4 py-3 pr-16 rounded-xl border border-emerald-200 text-sm" />
            <button type="button" onclick="window.kbApp.togglePasswordVisibility('login-password-input', this)" class="absolute right-2.5 top-1/2 -translate-y-1/2 px-2 py-1 text-xs font-bold text-[#168447] hover:bg-[#EAF7EF] rounded-lg">দেখুন</button>
          </div>
          <div class="text-right mt-1">
            <button type="button" onclick="window.kbApp.openForgotPasswordModal()" class="text-xs font-semibold text-[#168447] hover:underline">পাসওয়ার্ড ভুলে গেছেন?</button>
          </div>
        </div>
        <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">লগইন করুন</button>
      </form>
      <div class="flex justify-between text-xs font-semibold text-[#168447] pt-2 border-t border-emerald-50">
        <button onclick="window.kbApp.navigate('auth-register-farmer')" class="hover:underline">কৃষক নিবন্ধন →</button>
        <button onclick="window.kbApp.navigate('auth-register-buyer')" class="hover:underline">ক্রেতা নিবন্ধন →</button>
      </div>
    </div>
  `;
}

function renderRegisterPage(defaultRole) {
  const isFarmer = defaultRole === "FARMER";
  const initialDiv = "খুলনা";
  const initialDists = BD_DISTRICTS.filter((d) => d.divisionBn === initialDiv);
  const initialDist = "কুষ্টিয়া";
  const initialUpzs = BD_DISTRICTS.find((d) => d.districtBn === initialDist)?.upazilas || [];

  return `
    <div class="max-w-xl mx-auto bg-white rounded-3xl p-6 sm:p-8 border border-emerald-100 shadow-sm space-y-5">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-[#105B34]">${isFarmer ? "👨‍🌾 কৃষক নিবন্ধন" : "🛍️ ক্রেতা নিবন্ধন"}</h1>
          <p class="text-xs text-gray-600">কোনো OTP ছাড়াই সরাসরি মোবাইল নম্বর ও পাসওয়ার্ড দিয়ে নিবন্ধন করুন (সারা বাংলাদেশ)</p>
        </div>
        <div class="flex gap-1 bg-[#EAF7EF] p-1 rounded-xl text-xs font-bold">
          <button onclick="window.kbApp.navigate('auth-register-farmer')" class="px-3 py-1.5 rounded-lg ${isFarmer ? "bg-[#168447] text-white" : "text-[#105B34]"}">কৃষক</button>
          <button onclick="window.kbApp.navigate('auth-register-buyer')" class="px-3 py-1.5 rounded-lg ${!isFarmer ? "bg-[#168447] text-white" : "text-[#105B34]"}">ক্রেতা</button>
        </div>
      </div>

      <div class="bg-[#EAF7EF] border border-emerald-200 rounded-2xl p-3.5 text-xs text-[#105B34] space-y-1">
        <div class="font-bold">💡 ২ বছরের অ্যাকাউন্ট সক্রিয়করণ ফি: ৳${FIXED_ACTIVATION_FEE_BDT}</div>
        <p>নিবন্ধনের পর বিকাশ বা নগদে ৳${FIXED_ACTIVATION_FEE_BDT} পেমেন্ট তথ্য জমা দিয়ে অ্যাডমিন অনুমোদন পেলে ২ বছরের জন্য সকল পাইকারি কেনাবেচা চালু হবে।</p>
      </div>

      <form onsubmit="window.kbApp.handleRegisterSubmit(event)" class="space-y-3.5">
        <input type="hidden" name="role" value="${isFarmer ? "FARMER" : "BUYER"}" />
        <div class="grid sm:grid-cols-2 gap-3">
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">পূর্ণ নাম *</label>
            <input name="fullName" required placeholder="যেমন: মোঃ করিম মণ্ডল" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">মোবাইল নম্বর *</label>
            <input name="phone" required placeholder="017XXXXXXXX" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
        </div>

        <div class="grid sm:grid-cols-2 gap-3">
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর) *</label>
            <div class="relative">
              <input id="reg-password-input" name="password" type="password" minlength="6" required class="w-full px-3.5 py-2.5 pr-16 rounded-xl border border-emerald-200 text-sm" />
              <button type="button" onclick="window.kbApp.togglePasswordVisibility('reg-password-input', this)" class="absolute right-2 top-1/2 -translate-y-1/2 px-2 py-1 text-xs font-bold text-[#168447] hover:bg-[#EAF7EF] rounded-lg">দেখুন</button>
            </div>
          </div>
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">পাসওয়ার্ড নিশ্চিত করুন *</label>
            <div class="relative">
              <input id="reg-confirm-password-input" name="confirmPassword" type="password" minlength="6" required class="w-full px-3.5 py-2.5 pr-16 rounded-xl border border-emerald-200 text-sm" />
              <button type="button" onclick="window.kbApp.togglePasswordVisibility('reg-confirm-password-input', this)" class="absolute right-2 top-1/2 -translate-y-1/2 px-2 py-1 text-xs font-bold text-[#168447] hover:bg-[#EAF7EF] rounded-lg">দেখুন</button>
            </div>
          </div>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-2 bg-[#EAF7EF] px-3.5 py-2.5 rounded-2xl border border-emerald-200">
          <span id="reg-loc-status" class="text-xs font-bold text-[#105B34]">📍 ম্যাপ বা GPS থেকে আপনার এলাকা অটো নিন</span>
          <div class="flex gap-2">
            <button type="button" onclick="window.kbApp.detectBrowserLocation('register')" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">📍 GPS অটো</button>
            <button type="button" onclick="window.kbApp.openLocationMapModal('register')" class="px-3 py-1.5 rounded-xl bg-white border border-emerald-300 text-[#105B34] text-xs font-bold">🗺️ ম্যাপ খুলুন</button>
          </div>
        </div>

        <div class="grid sm:grid-cols-3 gap-3">
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">বিভাগ</label>
            <select id="reg-division" name="division" onchange="window.kbApp.onRegDivisionChange(this.value)" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
              ${BD_DIVISIONS.map((d) => `<option value="${escapeHtml(d)}" ${d === initialDiv ? "selected" : ""}>${escapeHtml(d)}</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">জেলা</label>
            <select id="reg-district" name="district" onchange="window.kbApp.onRegDistrictChange(this.value)" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
              ${initialDists.map((d) => `<option value="${escapeHtml(d.districtBn)}" ${d.districtBn === initialDist ? "selected" : ""}>${escapeHtml(d.districtBn)}</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">উপজেলা</label>
            <select id="reg-upazila" name="upazila" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
              ${initialUpzs.map((u) => `<option value="${escapeHtml(u)}">${escapeHtml(u)}</option>`).join("")}
            </select>
          </div>
        </div>

        <div class="grid sm:grid-cols-2 gap-3">
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">ইউনিয়ন / এলাকা</label>
            <input name="unionName" placeholder="যেমন: বাঁশগ্রাম" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">গ্রাম / বাড়ির ঠিকানা</label>
            <input name="village" placeholder="যেমন: শিমুলিয়া" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
        </div>

        <div class="grid ${isFarmer ? "sm:grid-cols-2" : ""} gap-3 pt-1">
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">এনআইডি শেষ ৪ সংখ্যা (ঐচ্ছিক)</label>
            <input name="nidLastFour" maxlength="4" placeholder="1234" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          ${
            isFarmer
              ? `<div>
                  <label class="text-xs font-bold text-gray-700 block mb-1">খামার/ফসল পরিচিতি</label>
                  <input name="farmBio" placeholder="যেমন: ধান ও মৌসুমি সবজি চাষি" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
                </div>`
              : ""
          }
        </div>

        <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">
          ${isFarmer ? "নিবন্ধন করে অ্যাক্টিভেশন ধাপে যান →" : "নিবন্ধন করে অ্যাক্টিভেশন ধাপে যান →"}
        </button>
      </form>
      <p class="text-center text-xs text-gray-600">ইতোমধ্যে অ্যাকাউন্ট আছে? <button onclick="window.kbApp.navigate('auth-login')" class="text-[#168447] font-bold hover:underline">লগইন করুন</button></p>
    </div>
  `;
}

function renderProfilePage() {
  const user = getActiveUser();
  if (!user) return renderLoginPage();
  const isFarmer = user.role === "FARMER";
  const st = user.accountStatus || "PAYMENT_PENDING";
  const stMeta = ACCOUNT_STATUS_META[st] || ACCOUNT_STATUS_META.PAYMENT_PENDING;
  const daysLeft = daysUntilExpiry(user);
  const dists = BD_DISTRICTS.filter((d) => d.divisionBn === (user.division || "খুলনা"));
  const upzs = BD_DISTRICTS.find((d) => d.districtBn === user.district)?.upazilas || dists[0]?.upazilas || [];

  return `
    <div class="max-w-3xl mx-auto space-y-6">
      <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div class="flex items-center gap-4">
          <div class="relative">
            ${
              user.profileImageUri
                ? `<img id="profile-avatar-preview" src="${escapeHtml(user.profileImageUri)}" alt="${escapeHtml(user.fullName)}" class="w-20 h-20 rounded-full object-cover border-2 border-[#168447]" />`
                : `<div id="profile-avatar-fallback" class="w-20 h-20 rounded-full bg-[#EAF7EF] border-2 border-emerald-200 flex items-center justify-center text-3xl">${isFarmer ? "👨‍🌾" : "🛍️"}</div><img id="profile-avatar-preview" class="hidden w-20 h-20 rounded-full object-cover border-2 border-[#168447]" alt="" />`
            }
          </div>
          <div class="space-y-1">
            <div class="flex flex-wrap items-center gap-2">
              <h1 class="text-xl sm:text-2xl font-bold text-[#105B34]">${escapeHtml(user.fullName)}</h1>
              <span class="px-2.5 py-0.5 rounded-full text-xs font-bold ${stMeta.color}">${stMeta.labelBn}</span>
            </div>
            <p class="text-xs text-gray-600">📱 ${escapeHtml(user.phone)} • ${isFarmer ? "কৃষক অ্যাকাউন্ট" : user.role === "ADMIN" ? "সুপার অ্যাডমিন" : "ক্রেতা অ্যাকাউন্ট"}</p>
            ${
              user.role !== "ADMIN"
                ? `<p class="text-xs font-semibold text-[#105B34]">
                    ${user.expiryDate > 0 ? `📅 মেয়াদ: ${formatDateBn(user.expiryDate)} (${daysLeft ?? 0} দিন বাকি)` : `⏳ ২ বছর মেয়াদের অ্যাক্টিভেশন ফি: ৳${FIXED_ACTIVATION_FEE_BDT}`}
                  </p>`
                : ""
            }
          </div>
        </div>
        ${
          user.role !== "ADMIN"
            ? `<div class="flex flex-wrap gap-2">
                <button onclick="window.kbApp.navigate('activation', { mode: 'account' })" class="px-4 py-2.5 rounded-xl bg-[#168447] text-white text-xs font-bold hover:bg-[#105B34]">
                  ${isMarketplaceActive(user) ? "🔄 মেয়াদ ও পেমেন্ট ইতিহাস" : "⚡ এখনই অ্যাকাউন্ট সক্রিয় করুন"}
                </button>
                <button onclick="window.kbApp.navigate('activation', { mode: 'ad_free' })" class="px-4 py-2.5 rounded-xl ${user.adsDisabled ? "bg-emerald-100 text-emerald-900 border border-emerald-300" : "bg-amber-50 text-amber-900 border border-amber-300"} text-xs font-bold">
                  ${user.adsDisabled ? "🚫 Ad-Free সক্রিয়" : `🚫 বিজ্ঞাপন বন্ধ (৳${Number(state.paymentSettings?.adRemovalFee || 200)})`}
                </button>
              </div>`
            : ""
        }
      </div>

      ${
        user.role !== "ADMIN"
          ? `<div class="rounded-3xl p-4 sm:p-5 border ${user.adsDisabled ? "bg-[#EAF7EF] border-emerald-200" : "bg-amber-50/70 border-amber-200"} flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="font-bold text-sm text-[#105B34]">${user.adsDisabled ? "🚫 বিজ্ঞাপন মুক্ত সুবিধা সক্রিয় রয়েছে" : "📢 সব বিজ্ঞাপন বন্ধ করুন (Ad-Free)"}</div>
                <p class="text-xs text-gray-600 mt-0.5">${user.adsDisabled ? "আপনার অ্যাকাউন্টে অ্যাপ ও ওয়েবসাইটের সব বিজ্ঞাপন বন্ধ আছে।" : `এককালীন ৳${Number(state.paymentSettings?.adRemovalFee || 200)} পেমেন্ট করে সব ব্যানার ও ভিডিও বিজ্ঞাপন স্থায়ীভাবে বন্ধ করুন।`}</p>
              </div>
              ${
                !user.adsDisabled
                  ? `<button onclick="window.kbApp.navigate('activation', { mode: 'ad_free' })" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">এখনই পরিশোধ করুন →</button>`
                  : `<span class="px-3 py-1 rounded-full bg-emerald-600 text-white text-xs font-bold">✓ Ad-Free</span>`
              }
            </div>`
          : ""
      }

      <div class="bg-white rounded-3xl p-6 sm:p-8 border border-emerald-100 shadow-sm space-y-4">
        <h2 class="text-lg font-bold text-[#105B34]">✏️ প্রোফাইল ও ছবি সম্পাদনা</h2>
        <form onsubmit="window.kbApp.handleProfileUpdateSubmit(event)" class="space-y-4">
          <div class="flex flex-wrap items-center gap-3 bg-[#F7FAF7] p-3.5 rounded-2xl border border-emerald-100">
            <label class="cursor-pointer px-4 py-2 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-[#105B34] text-xs font-bold hover:bg-emerald-100">
              🖼️ প্রোফাইল ছবি আপলোড
              <input type="file" accept="image/*" onchange="window.kbApp.handleCompressedImageUpload(this, 'profile-image-uri', 'profile-avatar-preview', 'profile-avatar-fallback')" class="hidden" />
            </label>
            <input type="hidden" name="profileImageUri" id="profile-image-uri" value="${escapeHtml(user.profileImageUri || "")}" />
            <span class="text-xs text-gray-500">গ্যালারি বা ক্যামেরা থেকে ছবি বেছে নিন</span>
          </div>

          <div class="grid sm:grid-cols-2 gap-3">
            <div>
              <label class="text-xs font-bold block mb-1">পূর্ণ নাম *</label>
              <input name="fullName" required value="${escapeHtml(user.fullName)}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">মোবাইল নম্বর (পরিবর্তনযোগ্য নয়)</label>
              <input value="${escapeHtml(user.phone)}" disabled class="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 bg-gray-50 text-sm text-gray-500" />
            </div>
          </div>

          <div class="grid sm:grid-cols-3 gap-3">
            <div>
              <label class="text-xs font-bold block mb-1">বিভাগ</label>
              <select id="prof-division" name="division" onchange="window.kbApp.onLocationCascadeChange('prof', this.value, null)" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
                ${BD_DIVISIONS.map((d) => `<option value="${escapeHtml(d)}" ${d === user.division ? "selected" : ""}>${escapeHtml(d)}</option>`).join("")}
              </select>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">জেলা</label>
              <select id="prof-district" name="district" onchange="window.kbApp.onLocationCascadeChange('prof', null, this.value)" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
                ${dists.map((d) => `<option value="${escapeHtml(d.districtBn)}" ${d.districtBn === user.district ? "selected" : ""}>${escapeHtml(d.districtBn)}</option>`).join("")}
              </select>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">উপজেলা</label>
              <select id="prof-upazila" name="upazila" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
                ${upzs.map((u) => `<option value="${escapeHtml(u)}" ${u === user.upazila ? "selected" : ""}>${escapeHtml(u)}</option>`).join("")}
              </select>
            </div>
          </div>

          <div class="grid sm:grid-cols-2 gap-3">
            <div>
              <label class="text-xs font-bold block mb-1">ইউনিয়ন / এলাকা</label>
              <input name="unionName" value="${escapeHtml(user.unionName || "")}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">গ্রাম / বাড়ির ঠিকানা</label>
              <input name="village" value="${escapeHtml(user.village || "")}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
          </div>

          <div class="grid sm:grid-cols-2 gap-3">
            <div>
              <label class="text-xs font-bold block mb-1">এনআইডি শেষ ৪ সংখ্যা (নতুন দিলে আপডেট হবে)</label>
              <input name="nidLastFour" maxlength="4" placeholder="${escapeHtml(user.nidMasked || "1234")}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">${isFarmer ? "খামার ও ফসল পরিচিতি" : "ঠিকানা / পরিচিতি নোট"}</label>
              <input name="farmBio" value="${escapeHtml(user.farmBio || "")}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
          </div>

          <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">
            💾 প্রোফাইল সংরক্ষণ করুন
          </button>
        </form>
      </div>
    </div>
  `;
}

function renderActivationPage() {
  const user = getActiveUser();
  if (!user) return renderLoginPage();
  if (user.role === "ADMIN") {
    return `<div class="bg-white rounded-3xl p-8 text-center"><p class="font-bold text-[#105B34]">সুপার অ্যাডমিনের অ্যাক্টিভেশন ফি প্রয়োজন নেই।</p><button onclick="window.kbApp.navigate('admin')" class="mt-3 px-4 py-2 rounded-xl bg-[#168447] text-white text-sm font-bold">অ্যাডমিন প্যানেল</button></div>`;
  }

  const settings = state.paymentSettings || DEFAULT_PAYMENT_SETTINGS;
  const st = user.accountStatus || "PAYMENT_PENDING";
  const stMeta = ACCOUNT_STATUS_META[st] || ACCOUNT_STATUS_META.PAYMENT_PENDING;
  const myPayments = state.activationPayments.filter((p) => p.userId === user.id);
  const mode = state.activationMode === "ad_free" ? "ad_free" : "account";
  const isAdRemovalMode = mode === "ad_free";
  const adRemovalFee = Number(settings.adRemovalFee || DEFAULT_PAYMENT_SETTINGS.adRemovalFee || 200);
  const hasPending = myPayments.some((p) => p.status === "PENDING" && ((p.paymentType === "AD_FREE") === isAdRemovalMode));
  const isRenewal = user.activationDate > 0 || st === "EXPIRED" || st === "ACTIVE";
  const payableAmount = isAdRemovalMode ? adRemovalFee : FIXED_ACTIVATION_FEE_BDT;

  return `
    <div class="max-w-3xl mx-auto space-y-6">
      <div class="bg-white rounded-2xl p-2 border border-emerald-100 flex gap-2">
        <button type="button" onclick="window.kbApp.setActivationMode('account')"
          class="flex-1 py-2.5 rounded-xl text-xs sm:text-sm font-bold ${!isAdRemovalMode ? "bg-[#168447] text-white" : "text-[#105B34] hover:bg-[#EAF7EF]"}">
          🔐 অ্যাকাউন্ট সক্রিয় / নবায়ন (৳${FIXED_ACTIVATION_FEE_BDT})
        </button>
        <button type="button" onclick="window.kbApp.setActivationMode('ad_free')"
          class="flex-1 py-2.5 rounded-xl text-xs sm:text-sm font-bold ${isAdRemovalMode ? "bg-[#168447] text-white" : "text-[#105B34] hover:bg-[#EAF7EF]"}">
          🚫 বিজ্ঞাপন বন্ধ (৳${adRemovalFee})
        </button>
      </div>

      <div class="bg-gradient-to-br from-[#105B34] to-[#168447] text-white rounded-3xl p-6 sm:p-8 shadow-md space-y-3">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="px-3 py-1 rounded-full bg-white/15 text-xs font-bold">
            ${isAdRemovalMode ? "🚫 সব বিজ্ঞাপন স্থায়ীভাবে বন্ধ (Ad-Free)" : "🔐 অ্যাকাউন্ট অ্যাক্টিভেশন ও ২ বছর মেয়াদ"}
          </span>
          <span class="px-3 py-1 rounded-full text-xs font-bold ${isAdRemovalMode && user.adsDisabled ? "bg-emerald-300 text-[#105B34]" : stMeta.color}">
            ${isAdRemovalMode ? (user.adsDisabled ? "✓ Ad-Free সক্রিয়" : "বিজ্ঞাপন চালু") : stMeta.labelBn}
          </span>
        </div>
        <h1 class="text-2xl font-extrabold">
          ${isAdRemovalMode ? `বিজ্ঞাপন মুক্ত করার ফি: ৳${adRemovalFee} (এককালীন)` : `নির্ধারিত ফি: ৳${FIXED_ACTIVATION_FEE_BDT} (${FIXED_VALIDITY_YEARS} বছরের মেয়াদ)`}
        </h1>
        <p class="text-xs sm:text-sm text-emerald-50/90">${escapeHtml(settings.paymentInstructions || DEFAULT_PAYMENT_SETTINGS.paymentInstructions)}</p>
        ${!isAdRemovalMode && user.expiryDate > 0 ? `<div class="text-xs bg-white/15 rounded-2xl px-4 py-2 inline-block">বর্তমান মেয়াদ: ${formatDateBn(user.expiryDate)} (${daysUntilExpiry(user) ?? 0} দিন বাকি)</div>` : ""}
      </div>

      <!-- Admin Official Payment Numbers -->
      <div class="grid sm:grid-cols-2 gap-4">
        <div class="bg-white rounded-3xl p-5 border border-pink-200 shadow-sm space-y-2">
          <div class="flex items-center justify-between">
            <span class="font-extrabold text-pink-700 text-base">bKash (বিকাশ)</span>
            <span class="px-2 py-0.5 rounded-full text-[11px] font-bold bg-pink-50 text-pink-800 border border-pink-200">${escapeHtml(settings.bKashAccountType || "PERSONAL")}</span>
          </div>
          <div class="text-xl font-extrabold text-[#202820] tracking-wide">${escapeHtml(settings.bKashNumber)}</div>
          <button type="button" onclick="window.kbApp.copyText('${escapeHtml(settings.bKashNumber)}')" class="px-3 py-1.5 rounded-xl bg-pink-50 text-pink-800 text-xs font-bold border border-pink-200">📋 নম্বর কপি করুন</button>
        </div>

        <div class="bg-white rounded-3xl p-5 border border-orange-200 shadow-sm space-y-2">
          <div class="flex items-center justify-between">
            <span class="font-extrabold text-orange-700 text-base">Nagad (নগদ)</span>
            <span class="px-2 py-0.5 rounded-full text-[11px] font-bold bg-orange-50 text-orange-800 border border-orange-200">${escapeHtml(settings.nagadAccountType || "PERSONAL")}</span>
          </div>
          <div class="text-xl font-extrabold text-[#202820] tracking-wide">${escapeHtml(settings.nagadNumber)}</div>
          <button type="button" onclick="window.kbApp.copyText('${escapeHtml(settings.nagadNumber)}')" class="px-3 py-1.5 rounded-xl bg-orange-50 text-orange-800 text-xs font-bold border border-orange-200">📋 নম্বর কপি করুন</button>
        </div>
      </div>

      <!-- Payment Submission Form -->
      <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm space-y-4">
        <h2 class="text-lg font-bold text-[#105B34]">
          ${isAdRemovalMode ? "🚫 বিজ্ঞাপন বন্ধ (Ad-Free) পেমেন্ট জমা দিন" : isRenewal ? "🔄 নবায়ন পেমেন্ট তথ্য জমা দিন" : "💳 অ্যাক্টিভেশন পেমেন্ট তথ্য জমা দিন"}
        </h2>
        ${
          isAdRemovalMode && user.adsDisabled
            ? `<div class="bg-[#EAF7EF] border border-emerald-200 rounded-2xl p-4 text-xs sm:text-sm text-[#105B34] font-bold">
                ✓ আপনার অ্যাকাউন্টের জন্য সব বিজ্ঞাপন ইতোমধ্যেই বন্ধ (Ad-Free) করা হয়েছে। আর কোনো পেমেন্টের প্রয়োজন নেই।
              </div>`
            : hasPending
            ? `<div class="bg-sky-50 border border-sky-200 rounded-2xl p-4 text-xs sm:text-sm text-sky-900 font-semibold">
                ⏳ আপনার একটি পেমেন্ট বর্তমানে অ্যাডমিন যাচাইয়ের জন্য অপেক্ষমাণ আছে। যাচাই সম্পন্ন হলেই নোটিফিকেশন পাবেন।
              </div>`
            : `<form onsubmit="window.kbApp.handleActivationPaymentSubmit(event)" class="space-y-3.5">
                <input type="hidden" name="isRenewal" value="${!isAdRemovalMode && isRenewal ? "1" : "0"}" />
                <input type="hidden" name="isAdRemoval" value="${isAdRemovalMode ? "1" : "0"}" />
                <div class="grid sm:grid-cols-2 gap-3">
                  <div>
                    <label class="text-xs font-bold block mb-1">পেমেন্ট মাধ্যম *</label>
                    <select name="paymentMethod" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
                      <option value="BKASH">bKash (বিকাশ)</option>
                      <option value="NAGAD">Nagad (নগদ)</option>
                    </select>
                  </div>
                  <div>
                    <label class="text-xs font-bold block mb-1">নির্ধারিত পরিমাণ (৳)</label>
                    <input name="amount" value="${payableAmount}" readonly class="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 bg-gray-50 text-sm font-bold text-[#105B34]" />
                  </div>
                </div>

                <div class="grid sm:grid-cols-2 gap-3">
                  <div>
                    <label class="text-xs font-bold block mb-1">যে নম্বর থেকে টাকা পাঠিয়েছেন *</label>
                    <input name="senderMobile" required value="${escapeHtml(user.phone)}" placeholder="017XXXXXXXX" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
                  </div>
                  <div>
                    <label class="text-xs font-bold block mb-1">Transaction ID (TxID) *</label>
                    <input name="transactionId" required placeholder="যেমন: 9FGH78KL21" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm uppercase" />
                  </div>
                </div>

                <div class="space-y-2">
                  <label class="text-xs font-bold block">পেমেন্ট রসিদ / স্ক্রিনশট ছবি (ঐচ্ছিক)</label>
                  <label class="inline-flex cursor-pointer px-4 py-2 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-[#105B34] text-xs font-bold hover:bg-emerald-100">
                    📎 রসিদ / স্ক্রিনশট আপলোড করুন
                    <input type="file" accept="image/*" onchange="window.kbApp.handleCompressedImageUpload(this, 'act-receipt-uri', 'act-receipt-preview')" class="hidden" />
                  </label>
                  <input type="hidden" name="receiptUri" id="act-receipt-uri" />
                  <img id="act-receipt-preview" class="hidden max-h-48 rounded-2xl border border-emerald-200 object-contain" alt="পেমেন্ট রসিদ প্রিভিউ" />
                </div>

                <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">
                  ✓ ৳${payableAmount} পেমেন্ট তথ্য জমা দিন
                </button>
              </form>`
        }
      </div>

      <!-- Submission History -->
      <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm space-y-3">
        <h3 class="font-bold text-[#105B34]">📜 আমার পেমেন্ট জমাদানের ইতিহাস (${myPayments.length})</h3>
        ${
          !myPayments.length
            ? `<p class="text-xs text-gray-500">এখনো কোনো পেমেন্ট জমা দেওয়া হয়নি।</p>`
            : `<div class="space-y-2.5">
                ${myPayments
                  .map((p) => {
                    const pMeta = ACTIVATION_PAYMENT_STATUS_META[p.status] || ACTIVATION_PAYMENT_STATUS_META.PENDING;
                    const typeTag = p.paymentType === "AD_FREE" ? `<span class="px-2 py-0.5 rounded-full bg-purple-100 text-purple-800 font-bold text-[10px] mr-1">🚫 Ad-Free</span>` : "";
                    return `<div class="p-3.5 rounded-2xl bg-[#F7FAF7] border border-emerald-100 text-xs flex flex-wrap justify-between gap-2">
                      <div>
                        <div class="font-bold text-[#105B34]">${typeTag}${escapeHtml(p.paymentMethod)} • TxID: ${escapeHtml(p.transactionId)} • ${formatBdt(p.amount)}</div>
                        <div class="text-gray-600">প্রেরক: ${escapeHtml(p.senderMobile)} • ${new Date(p.submittedAt || p.createdAt).toLocaleString("bn-BD")}</div>
                        ${p.adminNotes ? `<div class="text-red-700 font-semibold mt-1">অ্যাডমিন নোট: ${escapeHtml(p.adminNotes)}</div>` : ""}
                      </div>
                      <span class="px-2.5 py-1 rounded-full h-fit font-bold ${pMeta.color}">${pMeta.labelBn}</span>
                    </div>`;
                  })
                  .join("")}
              </div>`
        }
      </div>
    </div>
  `;
}

function renderOrderSecuritySteps(o) {
  const verifiedAdv = Number(o.verifiedAdvanceTotal || 0);
  const due = Math.max(0, Number(o.grandTotal || 0) - verifiedAdv);
  const step1 = true;
  const step2 = Number(o.advanceAmountRequested || 0) > 0 || state.orderPayments.some((p) => p.orderId === o.id);
  const step3 = verifiedAdv > 0 || o.paymentStatus === "ZERO_ADVANCE_APPROVED";
  const step4 = ["CONFIRMED", "PROCESSING", "READY_FOR_PICKUP", "READY", "SHIPPED", "DELIVERED", "COMPLETED"].includes(o.status);
  const step5 = o.status === "COMPLETED" && due <= 0.01;
  const steps = [
    ["১. অর্ডার", step1],
    ["২. অগ্রিম তথ্য", step2],
    ["৩. কৃষক যাচাই", step3],
    ["৪. অর্ডার Confirmed", step4],
    ["৫. বকেয়া ও সম্পন্ন", step5]
  ];
  return `
    <div class="grid grid-cols-5 gap-1.5 pt-1">
      ${steps
        .map(
          ([label, done]) => `
          <div class="px-2 py-1.5 rounded-xl text-center text-[10px] font-bold border ${
            done ? "bg-emerald-50 border-emerald-300 text-emerald-800" : "bg-gray-50 border-gray-200 text-gray-500"
          }">
            ${done ? "✓ " : "○ "}${label}
          </div>
        `
        )
        .join("")}
    </div>
  `;
}

function renderOrderPaymentBadgeRow(o) {
  const verifiedAdv = Number(o.verifiedAdvanceTotal || 0);
  const remainingDue = Math.max(0, Number(o.grandTotal || 0) - verifiedAdv);
  const payments = state.orderPayments.filter((p) => p.orderId === o.id);
  const pendingCount = payments.filter((p) => p.status === "PENDING_VERIFICATION").length;
  return `
    <div class="space-y-1.5 pt-1">
      <div class="flex flex-wrap items-center gap-1.5 text-[11px] font-bold">
        <span class="px-2.5 py-0.5 rounded-full bg-emerald-50 text-[#105B34] border border-emerald-200">মোট: ${formatBdt(o.grandTotal)}</span>
        <span class="px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-900">যাচাইকৃত অগ্রিম: ${formatBdt(verifiedAdv)}</span>
        <span class="px-2.5 py-0.5 rounded-full ${remainingDue > 0 ? "bg-amber-100 text-amber-900" : "bg-emerald-600 text-white"}">অবশিষ্ট বকেয়া: ${formatBdt(remainingDue)}</span>
        ${pendingCount > 0 ? `<span class="px-2.5 py-0.5 rounded-full bg-amber-600 text-white">${pendingCount}টি পেমেন্ট যাচাই বাকি</span>` : ""}
        <span class="px-2 py-0.5 rounded-full bg-gray-100 text-gray-700">পাইকারি লেনদেন (সরাসরি কৃষককে)</span>
      </div>
    </div>
  `;
}

function renderOrderPaymentsList(o, viewer) {
  const list = state.orderPayments.filter((p) => p.orderId === o.id);
  if (!list.length) return "";
  const isFarmer = viewer && viewer.id === o.farmerId && viewer.role === "FARMER";
  return `
    <div class="space-y-1.5 pt-1 border-t border-emerald-50">
      <div class="text-[11px] font-bold text-[#105B34]">📜 সরাসরি অগ্রিম ও আংশিক পেমেন্ট ইতিহাস (${list.length})</div>
      ${list
        .map((p) => {
          const pMeta = ORDER_PAYMENT_STATUS_META[p.status] || ORDER_PAYMENT_STATUS_META.PENDING_VERIFICATION;
          return `
            <div class="p-2.5 rounded-2xl bg-[#F7FAF7] border border-emerald-100 text-xs flex flex-wrap items-center justify-between gap-2">
              <div class="space-y-0.5">
                <div class="font-bold text-[#105B34]">${escapeHtml(p.paymentMethod)} • ${formatBdt(p.amount)} • TxID: <span class="font-mono">${escapeHtml(p.transactionId)}</span></div>
                <div class="text-[11px] text-gray-600">প্রেরক: ${escapeHtml(p.senderNumber || "-")} ${p.receiverNumber ? `→ প্রাপক: ${escapeHtml(p.receiverNumber)}` : ""} ${p.referenceNote ? `• নোট: ${escapeHtml(p.referenceNote)}` : ""}</div>
                ${p.rejectionReason ? `<div class="text-[11px] text-red-700 font-semibold">কারণ: ${escapeHtml(p.rejectionReason)}</div>` : ""}
              </div>
              <div class="flex flex-wrap items-center gap-1.5">
                <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${pMeta.color}">${pMeta.labelBn}</span>
                ${
                  isFarmer && p.status === "PENDING_VERIFICATION"
                    ? `<button onclick="window.kbApp.farmerVerifyOrderPayment('${escapeHtml(p.id)}', true)" class="px-2.5 py-1 rounded-xl bg-[#168447] text-white text-[11px] font-bold">✓ টাকা পেয়েছি (নিশ্চিত)</button>
                       <button onclick="window.kbApp.openRejectOrderPaymentModal('${escapeHtml(p.id)}')" class="px-2.5 py-1 rounded-xl bg-red-600 text-white text-[11px] font-bold">✕ প্রত্যাখ্যান</button>`
                    : ""
                }
              </div>
            </div>
          `;
        })
        .join("")}
    </div>
  `;
}

function renderFarmerDashboardPage() {
  const user = getActiveUser();
  if (!user) return renderLoginPage();
  if (user.role !== "FARMER") {
    return `<div class="bg-white rounded-3xl p-8 text-center space-y-3"><p class="font-bold text-red-700">এই পাতা শুধুমাত্র নিবন্ধিত কৃষকদের জন্য।</p><button onclick="window.kbApp.navigate('auth-register-farmer')" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-sm font-bold">কৃষক অ্যাকাউন্ট খুলুন</button></div>`;
  }

  const myProducts = state.products.filter((p) => p.farmerId === user.id);
  const myOrders = state.orders.filter((o) => o.farmerId === user.id);
  const totalSales = myOrders.filter((o) => !["CANCELLED", "REJECTED"].includes(o.status)).reduce((s, o) => s + Number(o.productTotal || 0), 0);
  const tab = state.farmerTab;
  const active = isMarketplaceActive(user);
  const stMeta = ACCOUNT_STATUS_META[user.accountStatus || "PAYMENT_PENDING"] || ACCOUNT_STATUS_META.PAYMENT_PENDING;

  return `
    <div class="space-y-6">
      ${renderAccountStatusBanner(user)}

      <!-- Farmer Header -->
      <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div class="flex items-center gap-3">
          ${
            user.profileImageUri
              ? `<img src="${escapeHtml(user.profileImageUri)}" alt="" class="w-14 h-14 rounded-full object-cover border border-emerald-200" />`
              : `<div class="w-14 h-14 rounded-full bg-[#EAF7EF] flex items-center justify-center text-2xl">👨‍🌾</div>`
          }
          <div>
            <div class="flex flex-wrap items-center gap-2">
              <h1 class="text-2xl font-bold text-[#105B34]">👨‍🌾 কৃষক পাইকারি ড্যাশবোর্ড</h1>
              <span class="px-2.5 py-0.5 rounded-full text-xs font-bold ${stMeta.color}">${stMeta.labelBn}</span>
              ${user.verificationStatus === "VERIFIED" ? `<span class="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800">✓ যাচাইকৃত</span>` : ""}
            </div>
            <p class="text-xs text-gray-600">${escapeHtml(user.fullName)} • 📍 ${escapeHtml(user.unionName ? user.unionName + ", " : "")}${escapeHtml(user.upazila)}, ${escapeHtml(user.district)}, ${escapeHtml(user.division)}</p>
          </div>
        </div>
        <div class="flex flex-wrap gap-2 items-center">
          <div class="px-4 py-2 rounded-2xl bg-[#EAF7EF] text-center"><div class="font-extrabold text-[#105B34]">${myProducts.length}</div><div class="text-[11px]">আমার ফসল</div></div>
          <div class="px-4 py-2 rounded-2xl bg-[#EAF7EF] text-center"><div class="font-extrabold text-[#105B34]">${myOrders.length}</div><div class="text-[11px]">মোট অর্ডার</div></div>
          <div class="px-4 py-2 rounded-2xl bg-[#EAF7EF] text-center"><div class="font-extrabold text-[#105B34]">${formatBdt(totalSales)}</div><div class="text-[11px]">মোট পাইকারি বিক্রয়</div></div>
          <button onclick="window.kbApp.navigate('profile')" class="px-3.5 py-2 rounded-2xl border border-emerald-200 text-xs font-bold text-[#105B34] hover:bg-[#EAF7EF]">✏️ প্রোফাইল</button>
        </div>
      </div>

      <!-- Tabs -->
      <div class="flex flex-wrap gap-2">
        <button onclick="window.kbApp.setFarmerTab('orders')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "orders" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">📦 প্রাপ্ত অর্ডার (${myOrders.length})</button>
        <button onclick="window.kbApp.setFarmerTab('products')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "products" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">🌾 আমার ফসল (${myProducts.length})</button>
        <button onclick="window.kbApp.setFarmerTab('add')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "add" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">+ নতুন পাইকারি ফসল</button>
        <button onclick="window.kbApp.setFarmerTab('payment-settings')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "payment-settings" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">💳 পেমেন্ট সেটিংস</button>
      </div>

      ${
        tab === "orders"
          ? renderFarmerOrdersTab(myOrders, active, user)
          : tab === "products"
          ? renderFarmerProductsTab(myProducts, active)
          : tab === "payment-settings"
          ? renderFarmerPaymentSettingsTab(user)
          : renderAddProductTab(user, active)
      }
    </div>
  `;
}

function renderFarmerPaymentSettingsTab(user) {
  const fps = getFarmerPaymentSettings(user.id);
  return `
    <div class="bg-white rounded-3xl p-6 sm:p-8 border border-emerald-100 shadow-sm max-w-2xl space-y-4">
      <div>
        <h2 class="text-lg font-bold text-[#105B34]">💳 সরাসরি পেমেন্ট গ্রহণ সেটিংস (শুধুমাত্র আপনার অর্ডারে প্রদর্শিত)</h2>
        <p class="text-xs text-gray-600 mt-1">ক্রেতারা পাইকারি চেকআউটের সময় সরাসরি আপনার নির্বাচিত নম্বরে অগ্রিম টাকা পাঠাবেন। প্ল্যাটফর্ম কখনোই এই টাকা গ্রহণ বা কেটে রাখে না। অন্য কোনো কৃষকের নম্বর কখনোই দেখানো হবে না।</p>
      </div>
      <form onsubmit="window.kbApp.handleFarmerPaymentSettingsSubmit(event)" class="space-y-3.5 text-sm">
        <div class="grid sm:grid-cols-2 gap-3">
          <div>
            <label class="text-xs font-bold block mb-1">bKash (বিকাশ) নম্বর</label>
            <input name="bKashNumber" value="${escapeHtml(fps.bKashNumber || "")}" placeholder="017XXXXXXXX" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">Nagad (নগদ) নম্বর</label>
            <input name="nagadNumber" value="${escapeHtml(fps.nagadNumber || "")}" placeholder="018XXXXXXXX" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200" />
          </div>
        </div>
        <div class="grid sm:grid-cols-2 gap-3">
          <div>
            <label class="text-xs font-bold block mb-1">Rocket (রকেট) নম্বর</label>
            <input name="rocketNumber" value="${escapeHtml(fps.rocketNumber || "")}" placeholder="019XXXXXXXX" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">ব্যাংক অ্যাকাউন্ট তথ্য (ঐচ্ছিক)</label>
            <input name="bankAccountInfo" value="${escapeHtml(fps.bankAccountInfo || "")}" placeholder="ব্যাংক নাম, শাখা, অ্যাকাউন্ট নম্বর" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200" />
          </div>
        </div>
        <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold">
          💾 সরাসরি পেমেন্ট নম্বর সংরক্ষণ করুন
        </button>
      </form>
    </div>
  `;
}

function renderFarmerOrdersTab(myOrders, active, farmerUser) {
  if (!myOrders.length) {
    return `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200">এখনো কোনো অর্ডার আসেনি। ক্রেতা অর্ডার করলেই এখানে ও নোটিফিকেশনে সাথে সাথে দেখাবে।</div>`;
  }
  return `
    <div class="space-y-4">
      ${myOrders
        .map((o) => {
          const meta = ORDER_STATUS_META[o.status] || ORDER_STATUS_META.PAYMENT_PENDING;
          const farmerApproved = Boolean(o.farmerPaymentConfirmed || o.paymentStatus === "ZERO_ADVANCE_APPROVED");
          const canApproveZeroAdvance =
            active &&
            ["PAYMENT_PENDING", "PENDING"].includes(o.status) &&
            Number(o.advanceAmountRequested || 0) <= 0 &&
            !state.orderPayments.some((p) => p.orderId === o.id && p.status === "PENDING_VERIFICATION");
          return `
            <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <span class="font-extrabold text-[#105B34]">${escapeHtml(o.orderCode)}</span>
                  <span class="text-xs text-gray-500 ml-2">${new Date(o.createdAt).toLocaleString("bn-BD")}</span>
                </div>
                <span class="px-3 py-1 rounded-full text-xs font-bold ${meta.color}">${meta.labelBn}</span>
              </div>
              <div class="text-sm font-semibold">${escapeHtml(o.itemsSummary)}</div>
              <div class="text-xs text-gray-600 grid sm:grid-cols-2 gap-1">
                <div>ক্রেতা: <strong>${escapeHtml(o.buyerName)}</strong> (<a class="text-[#168447] underline" href="tel:${escapeHtml(o.buyerPhone)}">${escapeHtml(o.buyerPhone)}</a>)</div>
                <div>মোট পাইকারি মূল্য: <strong class="text-[#168447]">${formatBdt(o.grandTotal)}</strong> (${escapeHtml(o.paymentMethod)})</div>
                <div class="sm:col-span-2">ঠিকানা: ${escapeHtml(o.deliveryAddress)}</div>
              </div>
              ${renderOrderSecuritySteps(o)}
              ${renderOrderPaymentBadgeRow(o)}
              ${renderOrderPaymentsList(o, farmerUser || getActiveUser())}
              <div class="flex flex-wrap gap-2 pt-1">
                ${
                  ["PAYMENT_PENDING", "PENDING"].includes(o.status)
                    ? `
                      ${canApproveZeroAdvance ? `<button ${!active ? "disabled" : ""} onclick="window.kbApp.farmerApproveZeroAdvanceOrder('${escapeHtml(o.id)}')" class="px-3.5 py-2 rounded-xl bg-[#168447] disabled:opacity-50 text-white text-xs font-bold">✓ শূন্য-অগ্রিম অর্ডার অনুমোদন (Confirmed)</button>` : ""}
                      <button ${!active ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'REJECTED')" class="px-3.5 py-2 rounded-xl bg-red-600 disabled:opacity-50 text-white text-xs font-bold">✕ প্রত্যাখ্যান করুন</button>
                    `
                    : ""
                }
                ${o.status === "CONFIRMED" ? `<button ${!active ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'PROCESSING')" class="px-3.5 py-2 rounded-xl bg-sky-600 disabled:opacity-50 text-white text-xs font-bold">প্রস্তুত হচ্ছে</button>` : ""}
                ${o.status === "PROCESSING" ? `<button ${!active ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'READY_FOR_PICKUP')" class="px-3.5 py-2 rounded-xl bg-teal-600 disabled:opacity-50 text-white text-xs font-bold">সংগ্রহের জন্য প্রস্তুত</button>` : ""}
                ${["READY_FOR_PICKUP", "READY"].includes(o.status) ? `<button ${!active ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'SHIPPED')" class="px-3.5 py-2 rounded-xl bg-indigo-600 disabled:opacity-50 text-white text-xs font-bold">পথে পাঠানো হয়েছে</button>` : ""}
                ${
                  ["CONFIRMED", "PROCESSING", "READY_FOR_PICKUP", "READY", "SHIPPED", "DELIVERED"].includes(o.status)
                    ? `<button ${!active || !farmerApproved ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'COMPLETED')" class="px-3.5 py-2 rounded-xl bg-emerald-700 disabled:opacity-40 text-white text-xs font-bold">✓ ডেলিভারি ও সম্পূর্ণ সম্পন্ন</button>`
                    : ""
                }
              </div>
              <p class="text-[11px] text-amber-800 bg-amber-50 rounded-xl px-3 py-1.5 border border-amber-200">🔒 আপনার প্রকৃত bKash/Nagad/Rocket অ্যাকাউন্টে টাকা দেখে তবেই 'টাকা পেয়েছি (নিশ্চিত)' বাটনে চাপ দিন — কেবল স্ক্রিনশট বা ক্রেতার তথ্যে অর্ডার Confirmed হবে না।</p>
            </div>
          `;
        })
        .join("")}
    </div>
  `;
}

function renderFarmerProductsTab(myProducts, active) {
  if (!myProducts.length) {
    return `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200"><p>আপনার কোনো ফসল তালিকাভুক্ত নেই।</p><button onclick="window.kbApp.setFarmerTab('add')" class="mt-3 px-4 py-2 rounded-xl bg-[#168447] text-white text-sm font-bold">+ ফসল যোগ করুন</button></div>`;
  }
  return `
    <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
      ${myProducts
        .map((p) => {
          const stMeta = PRODUCT_STATUS_META[p.status] || PRODUCT_STATUS_META.PENDING_APPROVAL;
          return `
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex justify-between items-start gap-2">
              <div>
                <h3 class="font-bold text-base text-[#105B34]">${escapeHtml(p.nameBn)}</h3>
                <p class="text-xs text-gray-500">${escapeHtml(p.categoryNameBn)} • ${escapeHtml(p.district)} • ১ মণ = ${MON_TO_KG} কেজি</p>
              </div>
              <span class="px-2 py-0.5 rounded-full text-[11px] font-bold ${stMeta.color}">${escapeHtml(stMeta.labelBn)}</span>
            </div>
            <div class="grid grid-cols-2 gap-2 text-xs">
              <div>
                <label class="text-gray-500 block mb-1">প্রতি মণের দাম (৳)</label>
                <input id="price_${escapeHtml(p.id)}" type="number" min="1" step="0.5" value="${p.pricePerUnit}" ${!active ? "disabled" : ""} class="w-full px-3 py-2 rounded-xl border border-emerald-200 font-bold" />
              </div>
              <div>
                <label class="text-gray-500 block mb-1">মজুত (মণ)</label>
                <input id="stock_${escapeHtml(p.id)}" type="number" min="0" step="0.5" value="${p.availableQuantity}" ${!active ? "disabled" : ""} class="w-full px-3 py-2 rounded-xl border border-emerald-200 font-bold" />
              </div>
            </div>
            <div class="text-[11px] text-emerald-800 font-semibold">মোট ওজন: ${formatQty(Number(p.availableQuantity || 0) * MON_TO_KG)} কেজি</div>
            <div class="flex gap-2">
              <button ${!active ? "disabled" : ""} onclick="window.kbApp.updateFarmerPriceAndStock('${escapeHtml(p.id)}', document.getElementById('price_${escapeHtml(p.id)}').value, document.getElementById('stock_${escapeHtml(p.id)}').value)"
                class="flex-1 py-2.5 rounded-xl bg-[#168447] disabled:opacity-50 text-white text-xs font-bold hover:bg-[#105B34]">
                আপডেট সংরক্ষণ
              </button>
              <button onclick="window.kbApp.confirmDeleteProduct('${escapeHtml(p.id)}')"
                class="px-3 py-2.5 rounded-xl bg-red-50 text-red-700 border border-red-200 text-xs font-bold hover:bg-red-100">
                🗑️ মুছুন
              </button>
            </div>
          </div>
        `;
        })
        .join("")}
    </div>
  `;
}

function renderAddProductTab(user, active) {
  return `
    <div class="bg-white rounded-3xl p-6 sm:p-8 border border-emerald-100 shadow-sm max-w-2xl space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h2 class="text-lg font-bold text-[#105B34]">নতুন ফসল যুক্ত করুন (০% কমিশন)</h2>
        <span class="px-3 py-1 rounded-full bg-[#EAF7EF] text-[#105B34] text-xs font-bold">বিক্রয়ের একক: মণ (১ মণ = ${MON_TO_KG} কেজি)</span>
      </div>
      ${
        !active
          ? `<div class="bg-amber-50 border border-amber-200 rounded-2xl p-4 text-xs text-amber-900 font-semibold">
              🔒 নতুন ফসল যুক্ত করতে আগে ৳${FIXED_ACTIVATION_FEE_BDT} ফি দিয়ে আপনার অ্যাকাউন্ট সক্রিয় করুন।
              <button onclick="window.kbApp.navigate('activation')" class="underline font-bold ml-1">অ্যাক্টিভেশন পাতায় যান →</button>
            </div>`
          : ""
      }
      <form onsubmit="window.kbApp.handleCreateProductSubmit(event)" class="space-y-4">
        <div class="grid sm:grid-cols-2 gap-3">
          <div>
            <label class="text-xs font-bold block mb-1">ফসলের নাম *</label>
            <input name="nameBn" required ${!active ? "disabled" : ""} placeholder="যেমন: ধান / দেশি মিনিকেট চাল" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">ক্যাটাগরি *</label>
            <select name="categoryId" ${!active ? "disabled" : ""} class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
              ${state.categories.map((c) => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.emoji || "")} ${escapeHtml(c.nameBn)}</option>`).join("")}
            </select>
          </div>
        </div>

        <div class="grid grid-cols-3 gap-3">
          <div>
            <label class="text-xs font-bold block mb-1">পরিমাণ (মণ) *</label>
            <input id="add-prod-qty" name="quantity" type="number" min="0.5" step="0.5" required ${!active ? "disabled" : ""} placeholder="10"
              oninput="window.kbApp.updateAddProductPreview()"
              class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">বিক্রয়ের একক</label>
            <input type="hidden" name="unit" value="মণ" />
            <input value="মণ (${MON_TO_KG} কেজি)" disabled class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-[#EAF7EF] font-bold text-[#105B34]" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">প্রতি মণের দাম (৳) *</label>
            <input id="add-prod-price" name="pricePerUnit" type="number" min="1" step="0.5" required ${!active ? "disabled" : ""} placeholder="1300"
              oninput="window.kbApp.updateAddProductPreview()"
              class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
        </div>

        <div id="add-prod-preview" class="bg-[#EAF7EF] border border-emerald-200 rounded-2xl px-4 py-2.5 text-xs font-bold text-[#105B34] flex flex-wrap justify-between gap-2">
          <span>মোট ওজন: ০ কেজি</span>
          <span>মোট বিক্রয় মূল্য: ৳০</span>
        </div>

        <div>
          <label class="text-xs font-bold block mb-1">বিস্তারিত বিবরণ</label>
          <textarea name="descriptionBn" rows="2" ${!active ? "disabled" : ""} placeholder="ফসলের মান, জাত এবং তাজা অবস্থা সম্পর্কে লিখুন..." class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm"></textarea>
        </div>

        <div class="grid sm:grid-cols-2 gap-3">
          <div class="space-y-2">
            <label class="text-xs font-bold block">ফসলের ছবি (ক্যামেরা / ফাইল ও গ্যালারি)</label>
            <div class="flex flex-wrap gap-2">
              <label class="cursor-pointer px-3 py-2 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-[#105B34] text-xs font-bold hover:bg-emerald-100">
                📷 ক্যামেরা
                <input type="file" accept="image/*" capture="environment" ${!active ? "disabled" : ""} onchange="window.kbApp.handleProductImageUpload(this)" class="hidden" />
              </label>
              <label class="cursor-pointer px-3 py-2 rounded-xl bg-white border border-emerald-200 text-[#105B34] text-xs font-bold hover:bg-[#EAF7EF]">
                🖼️ গ্যালারি / ফাইল
                <input type="file" accept="image/*" ${!active ? "disabled" : ""} onchange="window.kbApp.handleProductImageUpload(this)" class="hidden" />
              </label>
            </div>
            <img id="new-prod-image-preview" class="hidden w-full h-28 object-cover rounded-2xl border border-emerald-200" alt="ফসলের ছবি প্রিভিউ" />
            <input type="hidden" name="imageUri" id="new-prod-image-uri" />
          </div>
          <div class="space-y-1.5">
            <div class="flex items-center justify-between">
              <label class="text-xs font-bold">সংগ্রহ ঠিকানা</label>
              <div class="flex gap-1">
                <button type="button" onclick="window.kbApp.detectBrowserLocation('pickup')" class="px-2 py-0.5 rounded-lg bg-[#EAF7EF] text-[#105B34] text-[11px] font-bold">📍 GPS</button>
                <button type="button" onclick="window.kbApp.openLocationMapModal('pickup')" class="px-2 py-0.5 rounded-lg border border-emerald-200 text-[#105B34] text-[11px] font-bold">🗺️ ম্যাপ</button>
              </div>
            </div>
            <input id="new-prod-pickup-address" name="pickupAddress" ${!active ? "disabled" : ""} value="${escapeHtml(`${user.unionName ? user.unionName + ", " : ""}${user.upazila}, ${user.district}`)}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
        </div>

        <div class="grid sm:grid-cols-3 gap-3 items-center bg-[#F7FAF7] p-3.5 rounded-2xl text-xs font-semibold">
          <label class="flex items-center gap-2"><input type="checkbox" name="supportsFarmPickup" checked ${!active ? "disabled" : ""} /> খামার থেকে সংগ্রহ</label>
          <label class="flex items-center gap-2"><input type="checkbox" name="supportsDirectDelivery" checked ${!active ? "disabled" : ""} /> সরাসরি ডেলিভারি</label>
          <span class="px-3 py-1.5 rounded-xl bg-amber-100 text-amber-900 font-bold text-center">📦 পাইকারি মাত্র (১ মণ = ${MON_TO_KG} কেজি)</span>
        </div>

        <div class="grid sm:grid-cols-2 gap-3">
          <div>
            <label class="text-xs font-bold block mb-1">ডেলিভারি চার্জ (৳)</label>
            <input name="deliveryCharge" type="number" min="0" value="60" ${!active ? "disabled" : ""} class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">ফসল তোলার তারিখ</label>
            <input name="harvestDate" placeholder="আজই সংগ্রহ" ${!active ? "disabled" : ""} class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
        </div>

        <button type="submit" ${!active ? "disabled" : ""} class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] disabled:opacity-50 text-white font-bold text-sm">
          ফসল তালিকাভুক্ত করুন (মণ হিসেবে)
        </button>
      </form>
    </div>
  `;
}

function renderCartPage() {
  const user = getActiveUser();
  if (!state.cart.length) {
    return `
      <div class="bg-white rounded-3xl p-12 text-center border border-emerald-100 max-w-lg mx-auto space-y-3">
        <div class="text-5xl">🛒</div>
        <h1 class="text-xl font-bold text-[#105B34]">আপনার পাইকারি কার্ট খালি</h1>
        <p class="text-sm text-gray-600">তাজা কৃষিপণ্য মণ হিসেবে কার্টে যোগ করে সরাসরি কৃষকের কাছ থেকে পাইকারি অর্ডার করুন।</p>
        <button onclick="window.kbApp.navigate('browse')" class="px-5 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">ফসল খুঁজুন</button>
      </div>
    `;
  }

  const productTotal = state.cart.reduce((s, c) => s + Number(c.pricePerUnit || 0) * Number(c.quantity || 0), 0);
  const totalMon = state.cart.reduce((s, c) => s + Number(c.quantity || 0), 0);
  const totalKg = totalMon * MON_TO_KG;
  const active = user && isMarketplaceActive(user);

  // Per-farmer isolated payment channels: ONLY farmers present in the buyer's cart!
  const farmerIdsInCart = Array.from(new Set(state.cart.map((c) => c.farmerId)));
  const farmerChannelsHtml = farmerIdsInCart
    .map((fId) => {
      const fName = state.cart.find((c) => c.farmerId === fId)?.farmerName || "কৃষক";
      const fps = getFarmerPaymentSettings(fId);
      const lines = [
        fps.bKashNumber ? `bKash: <strong>${escapeHtml(fps.bKashNumber)}</strong>` : "",
        fps.nagadNumber ? `Nagad: <strong>${escapeHtml(fps.nagadNumber)}</strong>` : "",
        fps.rocketNumber ? `Rocket: <strong>${escapeHtml(fps.rocketNumber)}</strong>` : "",
        fps.bankAccountInfo ? `Bank: <strong>${escapeHtml(fps.bankAccountInfo)}</strong>` : ""
      ].filter(Boolean);
      return `
        <div class="p-3 rounded-2xl bg-[#EAF7EF] border border-emerald-200 text-xs space-y-1">
          <div class="font-bold text-[#105B34]">👨‍🌾 ${escapeHtml(fName)} — সরাসরি পেমেন্ট নম্বর</div>
          ${lines.length ? `<div class="text-gray-800 space-y-0.5">${lines.map((l) => `<div>• ${l}</div>`).join("")}</div>` : `<div class="text-amber-800">এই কৃষক এখনো ডিজিটাল নম্বর সেট করেননি (শূন্য অগ্রিম ৳০ বা সরাসরি নগদ দিয়ে অর্ডার করুন)</div>`}
        </div>
      `;
    })
    .join("");

  return `
    <div class="space-y-4">
      ${user ? renderAccountStatusBanner(user) : ""}
      <div class="grid lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2 space-y-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-2xl font-bold text-[#105B34]">🛒 পাইকারি শপিং কার্ট (${state.cart.length}টি আইটেম)</h1>
            <span class="px-3 py-1 rounded-full bg-[#EAF7EF] text-[#105B34] text-xs font-bold">মোট: ${formatQty(totalMon)} মণ (${formatQty(totalKg)} কেজি)</span>
          </div>
          ${state.cart
            .map((c) => {
              const kg = Number(c.quantity || 0) * MON_TO_KG;
              return `
                <div class="bg-white rounded-2xl p-4 border border-emerald-100 flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <h3 class="font-bold text-sm text-[#202820]">${escapeHtml(c.productNameBn)} <span class="text-[10px] px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-bold">পাইকারি</span></h3>
                    <p class="text-xs text-gray-500">কৃষক: ${escapeHtml(c.farmerName)} • ${formatBdt(c.pricePerUnit)} / মণ (${formatQty(c.quantity)} মণ = ${formatQty(kg)} কেজি)</p>
                  </div>
                  <div class="flex items-center gap-2">
                    <button onclick="window.kbApp.updateCartQty('${escapeHtml(c.id)}', ${Number(c.quantity) - 0.5})" class="w-8 h-8 rounded-lg bg-[#EAF7EF] font-bold text-[#105B34]">-</button>
                    <span class="text-xs font-bold w-16 text-center">${formatQty(c.quantity)} মণ</span>
                    <button onclick="window.kbApp.updateCartQty('${escapeHtml(c.id)}', ${Number(c.quantity) + 0.5})" class="w-8 h-8 rounded-lg bg-[#EAF7EF] font-bold text-[#105B34]">+</button>
                    <span class="font-bold text-sm text-[#168447] ml-2 w-24 text-right">${formatBdt(c.pricePerUnit * c.quantity)}</span>
                  </div>
                </div>
              `;
            })
            .join("")}
        </div>

        <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm h-fit space-y-4">
          <h2 class="text-lg font-bold text-[#105B34]">পাইকারি চেকআউট ও সরাসরি অগ্রিম পেমেন্ট</h2>
          ${farmerChannelsHtml}
          <form onsubmit="window.kbApp.handleCheckoutSubmit(event)" class="space-y-3 text-sm">
            <div>
              <label class="text-xs font-bold block mb-1">সংগ্রহ / ডেলিভারি পদ্ধতি</label>
              <select name="deliveryOption" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 bg-white">
                <option value="FARM_PICKUP">সরাসরি কৃষকের খামার থেকে সংগ্রহ (০ ৳ চার্জ)</option>
                <option value="DIRECT_DELIVERY">সরাসরি হোম ডেলিভারি</option>
              </select>
            </div>
            <div>
              <div class="flex items-center justify-between mb-1">
                <label class="text-xs font-bold">ডেলিভারি ঠিকানা</label>
                <div class="flex gap-1">
                  <button type="button" onclick="window.kbApp.detectBrowserLocation('checkout')" class="px-2 py-0.5 rounded-lg bg-[#EAF7EF] text-[#105B34] text-[11px] font-bold">📍 GPS</button>
                  <button type="button" onclick="window.kbApp.openLocationMapModal('checkout')" class="px-2 py-0.5 rounded-lg border border-emerald-200 text-[#105B34] text-[11px] font-bold">🗺️ ম্যাপ</button>
                </div>
              </div>
              <input id="checkout-delivery-address" name="deliveryAddress" value="${escapeHtml(user ? `${user.village ? user.village + ", " : ""}${user.unionName ? user.unionName + ", " : ""}${user.upazila}, ${user.district}` : "")}" placeholder="বাড়ি, গ্রাম, ইউনিয়ন, উপজেলা, জেলা" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200" />
            </div>

            <div class="border-t border-emerald-100 pt-3 space-y-1.5">
              <div class="flex justify-between font-bold text-sm">
                <span>মোট পাইকারি অর্ডার মূল্য</span>
                <span class="text-[#168447]">${formatBdt(productTotal)}</span>
              </div>
              <div>
                <label class="text-xs font-bold block mb-1">Advance Payment Amount (অগ্রিম ৳০ – ${formatBdt(productTotal)})</label>
                <input id="checkout-advance-input" name="advanceAmount" type="number" min="0" max="${productTotal}" step="1" value="0"
                  oninput="window.kbApp.updateCartAdvancePreview(${productTotal})"
                  class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 font-bold text-[#105B34]" />
              </div>
              <div class="flex justify-between text-xs font-bold bg-amber-50 px-3 py-2 rounded-xl border border-amber-200">
                <span>অবশিষ্ট বকেয়া (Remaining Due)</span>
                <span id="checkout-remaining-due" class="text-amber-900">${formatBdt(productTotal)}</span>
              </div>
            </div>

            <div>
              <label class="text-xs font-bold block mb-1">পেমেন্ট মাধ্যম</label>
              <select name="paymentMethod" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 bg-white">
                <option value="BKASH">bKash (বিকাশ)</option>
                <option value="NAGAD">Nagad (নগদ)</option>
                <option value="ROCKET">Rocket (রকেট)</option>
                <option value="BANK_TRANSFER">ব্যাংক ট্রান্সফার</option>
                <option value="CASH_ON_PICKUP">খামারে সংগ্রহ / নগদ (৳০ অগ্রিম)</option>
              </select>
            </div>
            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="text-xs font-bold block mb-1">প্রেরক মোবাইল</label>
                <input name="senderNumber" value="${escapeHtml(user?.phone || "")}" placeholder="017XXXXXXXX" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
              </div>
              <div>
                <label class="text-xs font-bold block mb-1">Transaction ID</label>
                <input name="transactionRef" placeholder="অগ্রিম > ৳০ হলে দিন" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs uppercase" />
              </div>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">পেমেন্ট রেফারেন্স নোট (ঐচ্ছিক)</label>
              <input name="referenceNote" placeholder="যেমন: অগ্রিম অংশ বা ক্রেতার নোট" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>

            <p class="text-[11px] text-gray-500">টাকা সরাসরি কৃষকের নিজ অ্যাকাউন্টে পাঠান। প্ল্যাটফর্ম কখনোই টাকা নেয় বা কেটে রাখে না। কৃষক নিজ ব্যালেন্স দেখে নিশ্চিত করলেই অগ্রিম যোগ ও অর্ডার Confirmed হবে।</p>
            <button type="submit" ${user && !active ? "disabled" : ""} class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] disabled:opacity-50 text-white font-bold">পাইকারি অর্ডার জমা দিন</button>
          </form>
        </div>
      </div>
    </div>
  `;
}

function renderOrdersPage() {
  const user = getActiveUser();
  if (!user) return renderLoginPage();

  const active = isMarketplaceActive(user);
  const myOrders =
    user.role === "ADMIN"
      ? state.orders
      : user.role === "FARMER"
      ? state.orders.filter((o) => o.farmerId === user.id || o.buyerId === user.id)
      : state.orders.filter((o) => o.buyerId === user.id);

  const stMeta = ACCOUNT_STATUS_META[user.accountStatus || "PAYMENT_PENDING"] || ACCOUNT_STATUS_META.PAYMENT_PENDING;

  return `
    <div class="space-y-5">
      ${renderAccountStatusBanner(user)}
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex flex-wrap items-center gap-2">
          <h1 class="text-2xl font-bold text-[#105B34]">${
            user.role === "FARMER"
              ? `👨‍🌾 আমার অর্ডার ও সরবরাহ (${myOrders.length})`
              : user.role === "BUYER"
              ? `🛍️ আমার পাইকারি অর্ডার ও পেমেন্ট (${myOrders.length})`
              : `🛡️ সকল প্ল্যাটফর্ম অর্ডার (${myOrders.length})`
          }</h1>
          ${user.role !== "ADMIN" ? `<span class="px-2.5 py-1 rounded-full text-xs font-bold ${stMeta.color}">${stMeta.labelBn}</span>` : ""}
        </div>
        ${
          user.role === "FARMER"
            ? `<button onclick="window.kbApp.navigate('farmer-dashboard', { tab: 'orders' })" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">প্রাপ্ত অর্ডার গ্রহণ/পরিচালনা →</button>`
            : `<button onclick="window.kbApp.navigate('browse')" class="px-4 py-2 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold">+ আরও ফসল কিনুন</button>`
        }
      </div>
      ${
        !myOrders.length
          ? `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200">কোনো অর্ডার পাওয়া যায়নি।</div>`
          : myOrders
              .map((o) => {
                const meta = ORDER_STATUS_META[o.status] || ORDER_STATUS_META.PAYMENT_PENDING;
                const isBuyer = o.buyerId === user.id;
                const isFarmer = o.farmerId === user.id;
                const remainingDue = Math.max(0, Number(o.grandTotal || 0) - Number(o.verifiedAdvanceTotal || 0));
                const canCancel = isBuyer && ["PENDING", "PAYMENT_PENDING"].includes(o.status);
                const canSubmitPartialPayment =
                  active &&
                  isBuyer &&
                  remainingDue > 0 &&
                  !["CANCELLED", "REJECTED", "COMPLETED"].includes(o.status);
                const farmerApproved = Boolean(o.farmerPaymentConfirmed || o.paymentStatus === "ZERO_ADVANCE_APPROVED");
                const canApproveZeroAdvance =
                  active &&
                  isFarmer &&
                  ["PAYMENT_PENDING", "PENDING"].includes(o.status) &&
                  Number(o.advanceAmountRequested || 0) <= 0 &&
                  !state.orderPayments.some((p) => p.orderId === o.id && p.status === "PENDING_VERIFICATION");
                const canBuyerComplete =
                  active &&
                  isBuyer &&
                  ["CONFIRMED", "DELIVERED", "SHIPPED", "READY_FOR_PICKUP", "READY"].includes(o.status) &&
                  o.status !== "COMPLETED";
                const canReview = isBuyer && o.status === "COMPLETED";

                return `
                  <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
                    <div class="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <span class="font-extrabold text-[#105B34]">${escapeHtml(o.orderCode)}</span>
                        <span class="text-xs text-gray-500 ml-2">${new Date(o.createdAt).toLocaleString("bn-BD")}</span>
                      </div>
                      <span class="px-3 py-1 rounded-full text-xs font-bold ${meta.color}">${meta.labelBn}</span>
                    </div>
                    <div class="text-sm font-semibold">${escapeHtml(o.itemsSummary)}</div>
                    <div class="text-xs text-gray-600 grid sm:grid-cols-3 gap-2">
                      <div>কৃষক: <strong>${escapeHtml(o.farmerName)}</strong> (${active ? escapeHtml(o.farmerPhone || "-") : "🔒 সক্রিয় হলে দৃশ্যমান"})</div>
                      <div>এলাকা: ${escapeHtml(o.upazila)}, ${escapeHtml(o.district)}</div>
                      <div>মোট পাইকারি: <strong class="text-[#168447]">${formatBdt(o.grandTotal)}</strong> (${escapeHtml(o.paymentMethod)})</div>
                    </div>
                    ${renderOrderSecuritySteps(o)}
                    ${renderOrderPaymentBadgeRow(o)}
                    ${renderOrderPaymentsList(o, user)}
                    <div class="flex flex-wrap gap-2 pt-1">
                      ${
                        canSubmitPartialPayment
                          ? `<button onclick="window.kbApp.openOrderPaymentModal('${escapeHtml(o.id)}')" class="px-3.5 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">💳 অগ্রিম / আংশিক পেমেন্ট জমা (${formatBdt(remainingDue)} বাকি)</button>`
                          : ""
                      }
                      ${
                        canApproveZeroAdvance
                          ? `<button onclick="window.kbApp.farmerApproveZeroAdvanceOrder('${escapeHtml(o.id)}')" class="px-3.5 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">✓ শূন্য-অগ্রিম অর্ডার অনুমোদন</button>`
                          : ""
                      }
                      ${
                        canBuyerComplete
                          ? `<button ${!farmerApproved ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'COMPLETED')" class="px-3.5 py-1.5 rounded-xl bg-emerald-700 disabled:opacity-40 text-white text-xs font-bold">✓ ডেলিভারি সম্পন্ন</button>`
                          : ""
                      }
                      ${canCancel ? `<button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'CANCELLED')" class="px-3.5 py-1.5 rounded-xl bg-red-50 text-red-700 border border-red-200 text-xs font-bold">অর্ডার বাতিল করুন</button>` : ""}
                      ${canReview ? `<button onclick="window.kbApp.openReviewModal('${escapeHtml(o.id)}')" class="px-3.5 py-1.5 rounded-xl bg-amber-50 text-amber-800 border border-amber-200 text-xs font-bold">★ রিভিউ দিন</button>` : ""}
                      <button onclick="window.kbApp.openComplaintModal('${escapeHtml(o.id)}')" class="px-3.5 py-1.5 rounded-xl border border-gray-200 text-gray-700 text-xs font-semibold">অভিযোগ জানান</button>
                    </div>
                  </div>
                `;
              })
              .join("")
      }
    </div>
  `;
}

function renderNotificationsPage() {
  const visible = getVisibleNotifications();
  const browserPerm = typeof Notification !== "undefined" ? Notification.permission : "unsupported";

  return `
    <div class="max-w-3xl mx-auto space-y-5">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 class="text-2xl font-bold text-[#105B34]">🔔 নোটিফিকেশন ইনবক্স</h1>
          <p class="text-xs text-gray-600">নতুন অর্ডার, স্ট্যাটাস আপডেট ও সুপার অ্যাডমিনের ঘোষণা</p>
        </div>
        <div class="flex gap-2">
          ${browserPerm === "default" ? `<button onclick="window.kbApp.requestBrowserNotifications()" class="px-3 py-2 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs font-bold">ব্রাউজার অ্যালার্ট চালু করুন</button>` : ""}
          <button onclick="window.kbApp.markVisibleNotificationsRead()" class="px-3 py-2 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold">সব পঠিত চিহ্নিত করুন</button>
        </div>
      </div>

      ${
        !visible.length
          ? `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200">আপনার জন্য কোনো নোটিফিকেশন নেই।</div>`
          : visible
              .map(
                (n) => `
                <div class="bg-white rounded-2xl p-4 border ${n.isRead ? "border-emerald-100" : "border-[#168447] bg-[#EAF7EF]/40"} shadow-sm space-y-1">
                  <div class="flex justify-between items-start gap-2">
                    <div class="font-bold text-sm text-[#105B34]">${!n.isRead ? "🟢 " : ""}${escapeHtml(n.titleBn)}</div>
                    <span class="text-[11px] text-gray-500">${new Date(n.createdAt).toLocaleString("bn-BD")}</span>
                  </div>
                  <p class="text-sm text-gray-700">${escapeHtml(n.messageBn)}</p>
                  <div class="text-[11px] text-gray-500">ধরন: ${escapeHtml(n.type)} • প্রাপক: ${escapeHtml(n.targetRole || n.userId)}</div>
                </div>
              `
              )
              .join("")
      }
    </div>
  `;
}

function renderAdminDashboardPage() {
  const user = getActiveUser();
  if (!user || user.role !== "ADMIN") {
    return `<div class="bg-white rounded-3xl p-10 text-center max-w-md mx-auto"><h2 class="text-lg font-bold text-red-700">অনুমতি নেই</h2><p class="text-xs text-gray-600 mt-1">সুপার অ্যাডমিন ড্যাশবোর্ড শুধুমাত্র অ্যাডমিন লগইনের পর প্রদর্শিত হয়।</p><button onclick="window.kbApp.navigate('auth-login')" class="mt-4 px-5 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">অ্যাডমিন লগইন</button></div>`;
  }

  const farmers = state.users.filter((u) => u.role === "FARMER");
  const buyers = state.users.filter((u) => u.role === "BUYER");
  const nonAdminUsers = [...farmers, ...buyers];
  const verifiedFarmers = farmers.filter((u) => u.verificationStatus === "VERIFIED").length;
  const verifiedBuyers = buyers.filter((u) => u.verificationStatus === "VERIFIED").length;
  const activeUsersCount = nonAdminUsers.filter((u) => isMarketplaceActive(u)).length;
  const expiredUsersCount = nonAdminUsers.filter((u) => u.accountStatus === "EXPIRED").length;
  const suspendedUsersCount = nonAdminUsers.filter((u) => u.accountStatus === "SUSPENDED").length;
  const pendingActCount = state.activationPayments.filter((p) => p.status === "PENDING").length;
  const pendingProductsCount = state.products.filter((p) => p.status === "PENDING_APPROVAL").length;
  const orderPayVerifyCount = state.orders.filter((o) => !o.farmerPaymentConfirmed && !["PENDING", "CANCELLED", "REJECTED", "COMPLETED"].includes(o.status)).length;
  const validOrders = state.orders.filter((o) => !["CANCELLED", "REJECTED"].includes(o.status));
  const totalSales = validOrders.reduce((s, o) => s + Number(o.productTotal || 0), 0);
  const districtTotals = new Map();
  for (const o of validOrders) {
    const d = o.district || "অন্যান্য";
    districtTotals.set(d, (districtTotals.get(d) || 0) + Number(o.productTotal || 0));
  }

  const tab = state.adminTab;
  const tabs = [
    ["overview", "সারসংক্ষেপ"],
    ["payments", `💳 অ্যাক্টিভেশন পেমেন্ট${pendingActCount ? ` (${pendingActCount})` : ""}`],
    ["farmers", `কৃষক ও ক্রেতা (${nonAdminUsers.length})`],
    ["products", `🌾 ফসল যাচাই${pendingProductsCount ? ` (${pendingProductsCount})` : ""}`],
    ["orders", `অর্ডার (${state.orders.length})`],
    ["payment-settings", "⚙️ পেমেন্ট সেটিংস"],
    ["notifications", "🔔 নোটিফিকেশন পাঠান"],
    ["categories", "ক্যাটাগরি ও এলাকা"],
    ["complaints", `অভিযোগ (${state.complaints.length})`],
    ["ads", "💰 বিজ্ঞাপন ও সব নেটওয়ার্ক"],
    ["tips", `💡 ডেইলি টিপস (${state.dailyTips.length})`],
    ["sliders", `🖼️ হিরো স্লাইডার (${state.heroSliders.length})`],
    ["audit", "অডিট লগ"]
  ];

  let bodyHtml = "";
  if (tab === "overview") {
    const entries = Array.from(districtTotals.entries()).sort((a, b) => b[1] - a[1]);
    bodyHtml = `
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">অপেক্ষমাণ অ্যাক্টিভেশন ও ফসল যাচাই</div><div class="text-2xl font-extrabold text-amber-700">${pendingActCount + pendingProductsCount}</div><div class="text-[11px] text-gray-500 mt-1">পেমেন্ট: ${pendingActCount} • ফসল যাচাই বাকি: ${pendingProductsCount}</div></div>
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">সক্রিয় অ্যাকাউন্ট (২ বছর)</div><div class="text-2xl font-extrabold text-[#168447]">${activeUsersCount}</div><div class="text-[11px] text-gray-500 mt-1">মেয়াদোত্তীর্ণ: ${expiredUsersCount} • স্থগিত: ${suspendedUsersCount}</div></div>
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">মোট ফসল (প্রকাশিত/সব)</div><div class="text-2xl font-extrabold text-sky-700">${state.products.filter((p) => p.status === "ACTIVE").length} / ${state.products.length}</div><div class="text-[11px] text-gray-500 mt-1">কৃষক পেমেন্ট বাকি: ${orderPayVerifyCount} • অর্ডার: ${validOrders.length}</div></div>
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">মোট বিক্রয় (${verifiedFarmers} কৃষক • ${verifiedBuyers} ক্রেতা)</div><div class="text-2xl font-extrabold text-[#168447]">${formatBdt(totalSales)}</div></div>
      </div>
      <div class="bg-white rounded-3xl p-6 border border-emerald-100 space-y-3">
        <div class="flex justify-between items-center">
          <h3 class="font-bold text-[#105B34]">জেলাভিত্তিক বিক্রয় সারসংক্ষেপ (সারা বাংলাদেশ)</h3>
          <button onclick="window.kbApp.exportCsvReport()" class="px-3.5 py-2 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold">CSV রিপোর্ট ডাউনলোড</button>
        </div>
        ${
          !entries.length
            ? `<p class="text-sm text-gray-500">এখনো কোনো বিক্রয় রেকর্ড নেই।</p>`
            : entries
                .map(([dist, amt]) => {
                  const pct = totalSales > 0 ? Math.max(8, Math.round((amt / totalSales) * 100)) : 8;
                  return `<div class="space-y-1"><div class="flex justify-between text-xs font-bold"><span>${escapeHtml(dist)}</span><span>${formatBdt(amt)}</span></div><div class="h-2.5 rounded-full bg-[#EAF7EF] overflow-hidden"><div class="h-full bg-[#168447]" style="width:${pct}%"></div></div></div>`;
                })
                .join("")
        }
      </div>
    `;
  } else if (tab === "payments") {
    const pFilter = state.adminPaymentFilter || "ALL";
    const pQuery = String(state.adminPaymentQuery || "").trim().toLowerCase();
    const txCounts = new Map();
    for (const p of state.activationPayments) {
      const tx = normalizeTxId(p.transactionId);
      if (tx) txCounts.set(tx, (txCounts.get(tx) || 0) + 1);
    }
    const filteredPayments = state.activationPayments
      .filter((p) => (pFilter === "ALL" ? true : p.status === pFilter))
      .filter((p) => {
        if (!pQuery) return true;
        return (
          String(p.userName || "").toLowerCase().includes(pQuery) ||
          String(p.userPhone || "").toLowerCase().includes(pQuery) ||
          String(p.senderMobile || "").toLowerCase().includes(pQuery) ||
          String(p.transactionId || "").toLowerCase().includes(pQuery)
        );
      })
      .slice()
      .sort((a, b) => (b.submittedAt || 0) - (a.submittedAt || 0));

    bodyHtml = `
      <div class="space-y-4">
        <div class="bg-white rounded-2xl p-4 border border-emerald-100 space-y-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 class="font-bold text-sm text-[#105B34]">💳 অ্যাকাউন্ট অ্যাক্টিভেশন ও নবায়ন পেমেন্ট যাচাই (৳${FIXED_ACTIVATION_FEE_BDT} • ${FIXED_VALIDITY_YEARS} বছর)</h3>
              <p class="text-xs text-gray-600">অ্যাডমিন অনুমোদনের সাথে সাথেই ব্যবহারকারীর অ্যাকাউন্ট ২ বছরের জন্য সক্রিয় হবে। ডুপ্লিকেট TxID সতর্কতা প্রদর্শিত হয়।</p>
            </div>
            <input type="text" value="${escapeHtml(state.adminPaymentQuery || "")}" oninput="window.kbApp.setAdminPaymentQuery(this.value)" placeholder="নাম, মোবাইল বা TxID খুঁজুন..." class="px-3 py-2 rounded-xl border border-emerald-200 text-xs w-full sm:w-64" />
          </div>
          <div class="flex flex-wrap gap-2">
            ${[
              ["ALL", `সব (${state.activationPayments.length})`],
              ["PENDING", `অপেক্ষমাণ (${state.activationPayments.filter((p) => p.status === "PENDING").length})`],
              ["APPROVED", `অনুমোদিত (${state.activationPayments.filter((p) => p.status === "APPROVED").length})`],
              ["RESUBMISSION_REQUESTED", `পুনঃজমা (${state.activationPayments.filter((p) => p.status === "RESUBMISSION_REQUESTED").length})`],
              ["REJECTED", `প্রত্যাখ্যাত (${state.activationPayments.filter((p) => p.status === "REJECTED").length})`]
            ]
              .map(([k, lbl]) => `<button onclick="window.kbApp.setAdminPaymentFilter('${k}')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${pFilter === k ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">${lbl}</button>`)
              .join("")}
          </div>
        </div>

        <div class="space-y-3">
          ${
            !filteredPayments.length
              ? `<div class="bg-white rounded-3xl p-8 text-center text-sm text-gray-500">এই ফিল্টারে কোনো অ্যাক্টিভেশন পেমেন্ট পাওয়া যায়নি।</div>`
              : filteredPayments
                  .map((p) => {
                    const pMeta = ACTIVATION_PAYMENT_STATUS_META[p.status] || ACTIVATION_PAYMENT_STATUS_META.PENDING;
                    const isDupTx = (txCounts.get(normalizeTxId(p.transactionId)) || 0) > 1;
                    return `
                      <div class="bg-white rounded-2xl p-4 border ${isDupTx ? "border-red-300 bg-red-50/30" : "border-emerald-100"} flex flex-wrap items-center justify-between gap-3">
                        <div class="space-y-1 text-xs">
                          <div class="flex flex-wrap items-center gap-2">
                            <span class="font-bold text-sm text-[#105B34]">${escapeHtml(p.userName)} (${escapeHtml(p.userPhone)})</span>
                            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#EAF7EF] text-[#105B34]">${(p.userRole || p.accountRole) === "FARMER" ? "👨‍🌾 কৃষক" : "🛍️ ক্রেতা"}</span>
                            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${pMeta.color}">${pMeta.labelBn}</span>
                            ${p.paymentType === "AD_FREE" ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 text-purple-800">🚫 বিজ্ঞাপন বন্ধ (Ad-Free)</span>` : ""}
                            ${p.isRenewal && p.paymentType !== "AD_FREE" ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-100 text-indigo-800">🔄 নবায়ন</span>` : ""}
                            ${isDupTx ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-red-600 text-white">⚠️ ডুপ্লিকেট TxID</span>` : ""}
                          </div>
                          <div class="text-gray-700 font-semibold">${escapeHtml(p.paymentMethod)} • TxID: <span class="font-mono text-[#105B34]">${escapeHtml(p.transactionId)}</span> • পরিমাণ: <strong>${formatBdt(p.amount)}</strong>${p.paymentType === "AD_FREE" ? " (Ad-Free)" : ` (${p.validityYears || FIXED_VALIDITY_YEARS} বছর)`}</div>
                          <div class="text-gray-500">প্রেরক নম্বর: ${escapeHtml(p.senderMobile)} • জমা: ${new Date(p.submittedAt || p.createdAt).toLocaleString("bn-BD")}</div>
                          ${p.adminNotes ? `<div class="text-red-700 font-semibold">নোট: ${escapeHtml(p.adminNotes)}</div>` : ""}
                        </div>
                        <div class="flex flex-wrap gap-2">
                          ${p.receiptUri ? `<button onclick="window.kbApp.openReceiptPreviewModal('${escapeHtml(p.id)}', 'activation')" class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold border border-emerald-200">🖼️ রসিদ দেখুন</button>` : ""}
                          ${
                            p.status !== "APPROVED"
                              ? `<button onclick="window.kbApp.adminApproveActivationPayment('${escapeHtml(p.id)}')" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">${p.paymentType === "AD_FREE" ? "✓ Ad-Free অনুমোদন" : "✓ অনুমোদন (২ বছর)"}</button>
                                 <button onclick="window.kbApp.openRejectActivationModal('${escapeHtml(p.id)}', true)" class="px-3 py-1.5 rounded-xl bg-amber-600 text-white text-xs font-bold">↻ পুনঃজমা</button>
                                 <button onclick="window.kbApp.openRejectActivationModal('${escapeHtml(p.id)}', false)" class="px-3 py-1.5 rounded-xl bg-red-600 text-white text-xs font-bold">✕ প্রত্যাখ্যান</button>`
                              : ""
                          }
                        </div>
                      </div>
                    `;
                  })
                  .join("")
          }
        </div>
      </div>
    `;
  } else if (tab === "payment-settings") {
    const ps = state.paymentSettings || DEFAULT_PAYMENT_SETTINGS;
    bodyHtml = `
      <div class="max-w-2xl bg-white rounded-3xl p-6 sm:p-8 border border-emerald-100 shadow-sm space-y-4">
        <div>
          <h3 class="text-lg font-bold text-[#105B34]">⚙️ অ্যাক্টিভেশন ও পেমেন্ট সেটিংস</h3>
          <p class="text-xs text-gray-600">বিকাশ ও নগদ নম্বর, অ্যাকাউন্ট ধরন (Personal/Merchant), নির্দেশনা ও মেয়াদ রিমাইন্ডার নিয়ন্ত্রণ করুন।</p>
        </div>
        <form onsubmit="window.kbApp.handleAdminSavePaymentSettings(event)" class="space-y-4">
          <div class="grid sm:grid-cols-2 gap-3">
            <div>
              <label class="text-xs font-bold block mb-1">অ্যাক্টিভেশন ও নবায়ন ফি (নির্ধারিত)</label>
              <input value="৳${FIXED_ACTIVATION_FEE_BDT} (কৃষক ও ক্রেতা উভয়ের জন্য)" disabled class="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 bg-gray-50 text-xs font-bold text-[#105B34]" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">মেয়াদকাল (নির্ধারিত)</label>
              <input value="${FIXED_VALIDITY_YEARS} বছর (২ বছর প্যাকেজ)" disabled class="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 bg-gray-50 text-xs font-bold text-[#105B34]" />
            </div>
          </div>
          <div class="grid sm:grid-cols-2 gap-3">
            <div>
              <label class="text-xs font-bold block mb-1">bKash (বিকাশ) নম্বর *</label>
              <input name="bKashNumber" required value="${escapeHtml(ps.bKashNumber || DEFAULT_PAYMENT_SETTINGS.bKashNumber)}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">bKash অ্যাকাউন্ট ধরন</label>
              <select name="bKashAccountType" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
                <option value="PERSONAL" ${(ps.bKashAccountType || "PERSONAL") === "PERSONAL" ? "selected" : ""}>PERSONAL (পার্সোনাল)</option>
                <option value="MERCHANT" ${ps.bKashAccountType === "MERCHANT" ? "selected" : ""}>MERCHANT (মার্চেন্ট)</option>
              </select>
            </div>
          </div>
          <div class="grid sm:grid-cols-2 gap-3">
            <div>
              <label class="text-xs font-bold block mb-1">Nagad (নগদ) নম্বর *</label>
              <input name="nagadNumber" required value="${escapeHtml(ps.nagadNumber || DEFAULT_PAYMENT_SETTINGS.nagadNumber)}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Nagad অ্যাকাউন্ট ধরন</label>
              <select name="nagadAccountType" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
                <option value="PERSONAL" ${(ps.nagadAccountType || "PERSONAL") === "PERSONAL" ? "selected" : ""}>PERSONAL (পার্সোনাল)</option>
                <option value="MERCHANT" ${ps.nagadAccountType === "MERCHANT" ? "selected" : ""}>MERCHANT (মার্চেন্ট)</option>
              </select>
            </div>
          </div>
          <div class="grid sm:grid-cols-2 gap-3">
            <div>
              <label class="text-xs font-bold block mb-1">বিজ্ঞাপন মুক্ত করার ফি (Ad-Free Fee ৳) *</label>
              <input name="adRemovalFee" type="number" min="1" step="1" required value="${Number(ps.adRemovalFee || DEFAULT_PAYMENT_SETTINGS.adRemovalFee || 200)}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm font-bold text-[#105B34]" />
            </div>
            <div class="flex items-end pb-2 text-xs text-gray-600">
              ব্যবহারকারীরা এই ফি পরিশোধ করলে তাদের সব বিজ্ঞাপন বন্ধ হবে।
            </div>
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">ব্যবহারকারীদের জন্য পেমেন্ট নির্দেশনা</label>
            <textarea name="paymentInstructions" rows="3" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm">${escapeHtml(ps.paymentInstructions || DEFAULT_PAYMENT_SETTINGS.paymentInstructions)}</textarea>
          </div>
          <div class="bg-[#F7FAF7] p-4 rounded-2xl border border-emerald-100 space-y-2 text-xs font-bold">
            <div class="text-[#105B34] mb-1">মেয়াদ উত্তীর্ণ রিমাইন্ডার নোটিফিকেশন সেটিংস</div>
            <label class="flex items-center gap-2"><input type="checkbox" name="notify30Days" ${ps.notify30Days !== false ? "checked" : ""} /> মেয়াদ শেষ হওয়ার ৩০ দিন আগে নোটিফিকেশন</label>
            <label class="flex items-center gap-2"><input type="checkbox" name="notify7Days" ${ps.notify7Days !== false ? "checked" : ""} /> মেয়াদ শেষ হওয়ার ৭ দিন আগে নোটিফিকেশন</label>
            <label class="flex items-center gap-2"><input type="checkbox" name="notifyOnExpiry" ${ps.notifyOnExpiry !== false ? "checked" : ""} /> মেয়াদ উত্তীর্ণের দিনে নোটিফিকেশন</label>
          </div>
          <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">💾 পেমেন্ট সেটিংস সংরক্ষণ করুন</button>
        </form>
      </div>
    `;
  } else if (tab === "notifications") {
    const adminSent = state.notifications.filter((n) => n.senderRole === "ADMIN");
    bodyHtml = `
      <div class="grid lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm space-y-4">
          <h3 class="text-lg font-bold text-[#105B34]">📢 ব্যবহারকারীদের নোটিফিকেশন পাঠান</h3>
          <p class="text-xs text-gray-600">এখান থেকে পাঠানো নোটিফিকেশন Firebase Realtime Database-এর মাধ্যমে ওয়েবসাইট ও অ্যান্ড্রয়েড অ্যাপে তাৎক্ষণিক পৌঁছাবে।</p>
          <form onsubmit="window.kbApp.handleAdminSendNotification(event)" class="space-y-3.5">
            <div>
              <label class="text-xs font-bold block mb-1">প্রাপক গ্রুপ নির্বাচন</label>
              <select name="targetScope" onchange="document.getElementById('admin-specific-user-wrap').classList.toggle('hidden', this.value !== 'SPECIFIC')" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
                <option value="ALL_ALL">সকল ব্যবহারকারী (কৃষক + ক্রেতা)</option>
                <option value="ALL_FARMER">সকল কৃষক</option>
                <option value="ALL_BUYER">সকল ক্রেতা</option>
                <option value="SPECIFIC">নির্দিষ্ট একজন ব্যবহারকারী</option>
              </select>
            </div>
            <div id="admin-specific-user-wrap" class="hidden">
              <label class="text-xs font-bold block mb-1">ব্যবহারকারী নির্বাচন</label>
              <select name="specificUserId" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
                ${nonAdminUsers.map((u) => `<option value="${escapeHtml(u.id)}">${escapeHtml(u.fullName)} (${escapeHtml(u.role)} - ${escapeHtml(u.phone)})</option>`).join("")}
              </select>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">শিরোনাম *</label>
              <input name="titleBn" required placeholder="যেমন: জরুরি বাজার নির্দেশনা" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">মূল বার্তা *</label>
              <textarea name="messageBn" rows="3" required placeholder="সারা বাংলাদেশের কৃষক ও ক্রেতাদের জন্য বার্তা লিখুন..." class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm"></textarea>
            </div>
            <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">নোটিফিকেশন পাঠান</button>
          </form>
        </div>

        <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm space-y-3">
          <h3 class="text-base font-bold text-[#105B34]">সাম্প্রতিক প্রেরিত নোটিফিকেশন (${adminSent.length})</h3>
          <div class="space-y-2.5 max-h-96 overflow-y-auto">
            ${
              !adminSent.length
                ? `<p class="text-xs text-gray-500">এখনো অ্যাডমিন কোনো নোটিফিকেশন পাঠাননি।</p>`
                : adminSent
                    .map(
                      (n) => `<div class="p-3 rounded-2xl bg-[#F7FAF7] border border-emerald-100 text-xs space-y-1">
                        <div class="font-bold text-[#105B34]">${escapeHtml(n.titleBn)}</div>
                        <div class="text-gray-700">${escapeHtml(n.messageBn)}</div>
                        <div class="text-[11px] text-gray-500">প্রাপক: ${escapeHtml(n.targetRole || n.userId)} • ${new Date(n.createdAt).toLocaleString("bn-BD")}</div>
                      </div>`
                    )
                    .join("")
            }
          </div>
        </div>
      </div>
    `;
  } else if (tab === "farmers") {
    const uFilter = state.adminUserFilter || "ALL";
    const sFilter = state.adminUserStatusFilter || "ALL";
    const uQuery = String(state.adminUserSearch || "").trim().toLowerCase();
    const combinedUsers = (uFilter === "FARMER" ? farmers : uFilter === "BUYER" ? buyers : nonAdminUsers)
      .filter((u) => (sFilter === "ALL" ? true : (u.accountStatus || "PAYMENT_PENDING") === sFilter))
      .filter((u) => {
        if (!uQuery) return true;
        return (
          String(u.fullName || "").toLowerCase().includes(uQuery) ||
          String(u.phone || "").toLowerCase().includes(uQuery) ||
          String(u.district || "").toLowerCase().includes(uQuery) ||
          String(u.upazila || "").toLowerCase().includes(uQuery)
        );
      })
      .slice()
      .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

    bodyHtml = `
      <div class="space-y-4">
        <div class="bg-white rounded-2xl p-4 border border-emerald-100 space-y-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 class="font-bold text-sm text-[#105B34]">কৃষক ও ক্রেতা অ্যাকাউন্ট ব্যবস্থাপনা ও মেয়াদ নিয়ন্ত্রণ</h3>
              <p class="text-xs text-gray-600">সক্রিয়: ${activeUsersCount} • মেয়াদোত্তীর্ণ: ${expiredUsersCount} • স্থগিত: ${suspendedUsersCount}</p>
            </div>
            <input type="text" value="${escapeHtml(state.adminUserSearch || "")}" oninput="window.kbApp.setAdminUserSearch(this.value)" placeholder="নাম, মোবাইল বা জেলা খুঁজুন..." class="px-3 py-2 rounded-xl border border-emerald-200 text-xs w-full sm:w-64" />
          </div>
          <div class="flex flex-wrap gap-2">
            <button onclick="window.kbApp.setAdminUserFilter('ALL')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${uFilter === "ALL" ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">সবাই (${nonAdminUsers.length})</button>
            <button onclick="window.kbApp.setAdminUserFilter('FARMER')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${uFilter === "FARMER" ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">👨‍🌾 কৃষক (${farmers.length})</button>
            <button onclick="window.kbApp.setAdminUserFilter('BUYER')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${uFilter === "BUYER" ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">🛍️ ক্রেতা (${buyers.length})</button>
            <select onchange="window.kbApp.setAdminUserStatusFilter(this.value)" class="px-3 py-1.5 rounded-xl border border-emerald-200 text-xs font-bold bg-white">
              <option value="ALL" ${sFilter === "ALL" ? "selected" : ""}>সব অ্যাকাউন্ট অবস্থা</option>
              <option value="ACTIVE" ${sFilter === "ACTIVE" ? "selected" : ""}>সক্রিয় (ACTIVE)</option>
              <option value="PAYMENT_PENDING" ${sFilter === "PAYMENT_PENDING" ? "selected" : ""}>পেমেন্ট অপেক্ষমাণ</option>
              <option value="PAYMENT_UNDER_REVIEW" ${sFilter === "PAYMENT_UNDER_REVIEW" ? "selected" : ""}>যাচাই চলমান</option>
              <option value="PAYMENT_REJECTED" ${sFilter === "PAYMENT_REJECTED" ? "selected" : ""}>পেমেন্ট প্রত্যাখ্যাত</option>
              <option value="EXPIRED" ${sFilter === "EXPIRED" ? "selected" : ""}>মেয়াদোত্তীর্ণ (EXPIRED)</option>
              <option value="SUSPENDED" ${sFilter === "SUSPENDED" ? "selected" : ""}>স্থগিত (SUSPENDED)</option>
            </select>
          </div>
        </div>

        <div class="space-y-3">
          ${
            !combinedUsers.length
              ? `<div class="bg-white rounded-3xl p-8 text-center text-sm text-gray-500">এই ফিল্টারে কোনো নিবন্ধিত কৃষক বা ক্রেতা পাওয়া যায়নি।</div>`
              : combinedUsers
                  .map((person) => {
                    const isFarmer = person.role === "FARMER";
                    const accStatus = person.accountStatus || "PAYMENT_PENDING";
                    const accMeta = ACCOUNT_STATUS_META[accStatus] || ACCOUNT_STATUS_META.PAYMENT_PENDING;
                    return `<div class="bg-white rounded-2xl p-4 border border-emerald-100 flex flex-wrap items-center justify-between gap-3">
                      <div class="flex items-center gap-3">
                        ${person.profileImageUri ? `<img src="${escapeHtml(person.profileImageUri)}" alt="" class="w-11 h-11 rounded-full object-cover border border-emerald-200" />` : `<div class="w-11 h-11 rounded-full bg-[#EAF7EF] flex items-center justify-center text-lg">${isFarmer ? "👨‍🌾" : "🛍️"}</div>`}
                        <div class="space-y-1">
                          <div class="flex flex-wrap items-center gap-2">
                            <span class="font-bold text-sm text-[#105B34]">${escapeHtml(person.fullName)} (${escapeHtml(person.phone)})</span>
                            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${isFarmer ? "bg-emerald-50 text-emerald-700 border border-emerald-200" : "bg-sky-50 text-sky-700 border border-sky-200"}">${isFarmer ? "কৃষক" : "ক্রেতা"}</span>
                            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${accMeta.color}">${accMeta.labelBn}</span>
                            ${person.adsDisabled ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 text-purple-800">🚫 Ad-Free</span>` : ""}
                          </div>
                          <div class="text-xs text-gray-600">${escapeHtml(person.unionName ? person.unionName + ", " : "")}${escapeHtml(person.upazila)}, ${escapeHtml(person.district)}, ${escapeHtml(person.division)} • NID: ${escapeHtml(person.nidMasked || "N/A")}</div>
                          <div class="text-[11px] text-gray-500">${person.expiryDate > 0 ? `মেয়াদ: ${formatDateBn(person.expiryDate)} (${daysUntilExpiry(person) ?? 0} দিন বাকি)` : "মেয়াদ এখনো শুরু হয়নি"}</div>
                        </div>
                      </div>
                      <div class="flex flex-wrap gap-2">
                        <button onclick="window.kbApp.adminSetUserAdsDisabled('${escapeHtml(person.id)}', ${person.adsDisabled ? "false" : "true"})"
                          class="px-3 py-1.5 rounded-xl ${person.adsDisabled ? "bg-purple-600 text-white" : "bg-purple-50 text-purple-900 border border-purple-200"} text-xs font-bold">
                          ${person.adsDisabled ? "📢 বিজ্ঞাপন চালু করুন" : "🚫 বিজ্ঞাপন বন্ধ করুন"}
                        </button>
                        ${accStatus !== "ACTIVE" ? `<button onclick="window.kbApp.adminSetAccountStatus('${escapeHtml(person.id)}', 'ACTIVE')" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">✓ সক্রিয় (২ বছর)</button>` : ""}
                        ${accStatus !== "SUSPENDED" ? `<button onclick="window.kbApp.adminSetAccountStatus('${escapeHtml(person.id)}', 'SUSPENDED')" class="px-3 py-1.5 rounded-xl bg-red-600 text-white text-xs font-bold">⏸ স্থগিত</button>` : ""}
                        ${person.verificationStatus !== "VERIFIED" ? `<button onclick="window.kbApp.adminVerifyUser('${escapeHtml(person.id)}', true)" class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] text-[#105B34] border border-emerald-200 text-xs font-bold">✓ যাচাই</button>` : ""}
                      </div>
                    </div>`;
                  })
                  .join("")
          }
        </div>
      </div>
    `;
  } else if (tab === "products") {
    const prodFilter = state.adminProductFilter || "PENDING_APPROVAL";
    const prodSearch = String(state.adminProductSearch || "").trim().toLowerCase();
    const filteredProducts = state.products
      .filter((p) => (prodFilter === "ALL" ? true : p.status === prodFilter))
      .filter((p) => {
        if (!prodSearch) return true;
        return (
          String(p.nameBn || "").toLowerCase().includes(prodSearch) ||
          String(p.farmerName || "").toLowerCase().includes(prodSearch) ||
          String(p.categoryNameBn || "").toLowerCase().includes(prodSearch) ||
          String(p.district || "").toLowerCase().includes(prodSearch)
        );
      })
      .slice()
      .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

    bodyHtml = `
      <div class="space-y-4">
        <div class="bg-white rounded-2xl p-4 border border-emerald-100 space-y-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 class="font-bold text-sm text-[#105B34]">🌾 কৃষকের ফসল যাচাই, অনুমোদন, সম্পাদনা ও বাতিল</h3>
              <p class="text-xs text-gray-600">কৃষক নতুন ফসল যোগ করলে এখানে জমা হয়। অ্যাডমিন অনুমোদন করলে তবেই সবার জন্য উন্মুক্ত হবে; প্রয়োজনে অ্যাডমিন সম্পাদনা বা মুছতে পারবেন।</p>
            </div>
            <input type="text" value="${escapeHtml(state.adminProductSearch || "")}" oninput="window.kbApp.setAdminProductSearch(this.value)" placeholder="ফসল, কৃষক বা জেলা খুঁজুন..." class="px-3 py-2 rounded-xl border border-emerald-200 text-xs w-full sm:w-64" />
          </div>
          <div class="flex flex-wrap gap-2">
            ${[
              ["PENDING_APPROVAL", `যাচাই বাকি (${state.products.filter((p) => p.status === "PENDING_APPROVAL").length})`],
              ["ALL", `সব ফসল (${state.products.length})`],
              ["ACTIVE", `প্রকাশিত (${state.products.filter((p) => p.status === "ACTIVE").length})`],
              ["REJECTED", `প্রত্যাখ্যাত (${state.products.filter((p) => p.status === "REJECTED").length})`]
            ]
              .map(([k, lbl]) => `<button onclick="window.kbApp.setAdminProductFilter('${k}')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${prodFilter === k ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">${lbl}</button>`)
              .join("")}
          </div>
        </div>

        <div class="space-y-3">
          ${
            !filteredProducts.length
              ? `<div class="bg-white rounded-3xl p-8 text-center text-sm text-gray-500">এই ফিল্টারে কোনো ফসল পাওয়া যায়নি।</div>`
              : filteredProducts
                  .map((p) => {
                    const stMeta = PRODUCT_STATUS_META[p.status] || PRODUCT_STATUS_META.PENDING_APPROVAL;
                    return `
                      <div class="bg-white rounded-2xl p-4 border border-emerald-100 flex flex-wrap items-center justify-between gap-3">
                        <div class="flex items-center gap-3">
                          ${
                            p.imageUri
                              ? `<img src="${escapeHtml(p.imageUri)}" alt="" class="w-14 h-14 rounded-2xl object-cover border border-emerald-200" />`
                              : `<div class="w-14 h-14 rounded-2xl bg-[#EAF7EF] flex items-center justify-center text-2xl">🌾</div>`
                          }
                          <div class="space-y-1 text-xs">
                            <div class="flex flex-wrap items-center gap-2">
                              <span class="font-bold text-sm text-[#105B34]">${escapeHtml(p.nameBn)}</span>
                              <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${stMeta.color}">${escapeHtml(stMeta.labelBn)}</span>
                            </div>
                            <div class="text-gray-700 font-semibold">${escapeHtml(p.categoryNameBn)} • ${formatBdt(p.pricePerUnit)}/মণ • মজুত: ${formatQty(p.availableQuantity)} মণ (${formatQty(Number(p.availableQuantity || 0) * MON_TO_KG)} কেজি)</div>
                            <div class="text-gray-500">কৃষক: <strong>${escapeHtml(p.farmerName)}</strong> • 📍 ${escapeHtml(p.upazila)}, ${escapeHtml(p.district)}</div>
                          </div>
                        </div>
                        <div class="flex flex-wrap gap-2">
                          ${p.status !== "ACTIVE" ? `<button onclick="window.kbApp.adminVerifyProduct('${escapeHtml(p.id)}', true)" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">✓ অনুমোদন ও প্রকাশ</button>` : ""}
                          ${p.status !== "REJECTED" ? `<button onclick="window.kbApp.openAdminRejectProductModal('${escapeHtml(p.id)}')" class="px-3 py-1.5 rounded-xl bg-amber-600 text-white text-xs font-bold">✕ প্রত্যাখ্যান</button>` : ""}
                          <button onclick="window.kbApp.openAdminEditProductModal('${escapeHtml(p.id)}')" class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] text-[#105B34] border border-emerald-200 text-xs font-bold">✏️ সম্পাদনা</button>
                          <button onclick="window.kbApp.confirmDeleteProduct('${escapeHtml(p.id)}')" class="px-3 py-1.5 rounded-xl bg-red-600 text-white text-xs font-bold">🗑️ মুছুন</button>
                        </div>
                      </div>
                    `;
                  })
                  .join("")
          }
        </div>
      </div>
    `;
  } else if (tab === "orders") {
    const oFilter = state.adminOrderFilter || "ALL";
    const filteredOrders =
      oFilter === "PAYMENT_VERIFY_REQ"
        ? state.orders.filter((o) => state.orderPayments.some((p) => p.orderId === o.id && p.status === "PENDING_VERIFICATION"))
        : state.orders;

    bodyHtml = `
      <div class="space-y-4">
        <div class="bg-white rounded-2xl p-4 border border-emerald-100 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 class="font-bold text-sm text-[#105B34]">পাইকারি অর্ডার ও সরাসরি পেমেন্ট অডিট লগ (Read-Only)</h3>
            <p class="text-xs text-gray-600">ক্রেতা সরাসরি কৃষককে অগ্রিম টাকা দেন — প্ল্যাটফর্ম/অ্যাডমিন কখনোই অর্ডারের টাকা গ্রহণ বা নিশ্চিত করেন না। শুধুমাত্র সংশ্লিষ্ট কৃষক টাকা দেখে নিশ্চিত করেন।</p>
          </div>
          <div class="flex gap-2">
            <button onclick="window.kbApp.setAdminOrderFilter('ALL')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${oFilter === "ALL" ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">সব অর্ডার (${state.orders.length})</button>
            <button onclick="window.kbApp.setAdminOrderFilter('PAYMENT_VERIFY_REQ')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${oFilter === "PAYMENT_VERIFY_REQ" ? "bg-[#105B34] text-white" : "bg-sky-50 text-sky-900 border border-sky-200"}">কৃষক যাচাই বাকি</button>
          </div>
        </div>

        <div class="space-y-3">
          ${
            !filteredOrders.length
              ? `<div class="bg-white rounded-2xl p-8 text-center text-sm text-gray-500">এই ফিল্টারে কোনো অর্ডার নেই।</div>`
              : filteredOrders
                  .map((o) => {
                    const meta = ORDER_STATUS_META[o.status] || ORDER_STATUS_META.PAYMENT_PENDING;
                    return `
                      <div class="bg-white rounded-2xl p-4 border border-emerald-100 space-y-2.5 text-xs">
                        <div class="flex flex-wrap justify-between items-center gap-2">
                          <div><strong class="text-sm text-[#105B34]">${escapeHtml(o.orderCode)}</strong> — ${escapeHtml(o.itemsSummary)}</div>
                          <div class="flex items-center gap-2">
                            <span class="font-bold text-[#168447] text-sm">${formatBdt(o.grandTotal)}</span>
                            <span class="px-2.5 py-0.5 rounded-full font-bold ${meta.color}">${meta.labelBn}</span>
                          </div>
                        </div>
                        <div class="text-gray-600 flex flex-wrap gap-3">
                          <span>কৃষক: <strong>${escapeHtml(o.farmerName)}</strong> (${escapeHtml(o.farmerPhone)})</span>
                          <span>ক্রেতা: <strong>${escapeHtml(o.buyerName)}</strong> (${escapeHtml(o.buyerPhone)})</span>
                          <span>মাধ্যম: ${escapeHtml(o.paymentMethod)}${o.paymentSenderNumber ? ` (${escapeHtml(o.paymentSenderNumber)})` : ""}</span>
                        </div>
                        ${renderOrderSecuritySteps(o)}
                        ${renderOrderPaymentBadgeRow(o)}
                        ${renderOrderPaymentsList(o, user)}
                      </div>
                    `;
                  })
                  .join("")
          }
        </div>
      </div>
    `;
  } else if (tab === "categories") {
    bodyHtml = `
      <div class="grid md:grid-cols-2 gap-6">
        <form onsubmit="window.kbApp.handleAdminAddCat(event)" class="bg-white rounded-3xl p-5 border border-emerald-100 space-y-3">
          <h3 class="font-bold text-[#105B34]">নতুন ক্যাটাগরি যোগ করুন</h3>
          <input name="nameBn" required placeholder="বাংলা নাম (যেমন: পেঁপে)" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          <input name="nameEn" placeholder="English Name (Papaya)" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          <input name="emoji" placeholder="ইমোজি (🍈)" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          <button type="submit" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">যোগ করুন</button>
        </form>
        <form onsubmit="window.kbApp.handleAdminAddLoc(event)" class="bg-white rounded-3xl p-5 border border-emerald-100 space-y-3">
          <h3 class="font-bold text-[#105B34]">নতুন এলাকা/ইউনিয়ন যোগ করুন</h3>
          <select name="divisionBn" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm bg-white">${BD_DIVISIONS.map((d) => `<option>${escapeHtml(d)}</option>`).join("")}</select>
          <input name="districtBn" required placeholder="জেলা" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          <input name="upazilaBn" required placeholder="উপজেলা" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          <input name="unionBn" placeholder="ইউনিয়ন" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          <button type="submit" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">এলাকা যুক্ত করুন</button>
        </form>
      </div>
    `;
  } else if (tab === "complaints") {
    bodyHtml = `<div class="space-y-3">${state.complaints.length ? state.complaints.map((c) => `<div class="bg-white rounded-2xl p-4 border border-emerald-100 flex justify-between items-center text-xs"><div><strong>${escapeHtml(c.subjectBn)}</strong> (${escapeHtml(c.reporterName)})<p class="text-gray-600">${escapeHtml(c.descriptionBn)}</p></div><button onclick="window.kbApp.adminResolveComplaint('${escapeHtml(c.id)}')" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white font-bold">${c.status === "RESOLVED" ? "✓ নিষ্পত্তি" : "সমাধান করুন"}</button></div>`).join("") : `<div class="bg-white rounded-2xl p-8 text-center">কোনো অভিযোগ নেই।</div>`}</div>`;
  } else if (tab === "ads") {
    const cfg = state.adConfig || DEFAULT_AD_CONFIG;
    const isLevelPlayActive = Boolean((cfg.mediationAppKey || "").trim());
    const hasStartIoId = Boolean((cfg.startIoAppId || "").trim());
    bodyHtml = `
      <div class="bg-white border border-emerald-200 rounded-3xl p-5 shadow-sm space-y-3 mb-5">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 class="text-base font-bold text-[#105B34]">🛡️ প্রোডাকশন ব্যবস্থাপনা ও app-ads.txt স্ট্যাটাস</h3>
            <p class="text-xs text-gray-600">প্লে স্টোর ও বিজ্ঞাপন নেটওয়ার্ক ভেরিফিকেশনের জন্য রিয়েল-টাইম প্রোডাকশন অবস্থা।</p>
          </div>
          <div class="flex flex-wrap gap-2 text-xs font-bold">
            <a href="./app-ads.txt" target="_blank" rel="noopener noreferrer" class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] text-[#105B34] border border-emerald-200 hover:bg-emerald-100">📄 app-ads.txt দেখুন ↗</a>
            <button type="button" onclick="navigator.clipboard?.writeText('start.io, 165433365, DIRECT'); window.kbApp && window.kbApp.showToast && window.kbApp.showToast('Start.io DIRECT লাইন কপি হয়েছে', 'success')" class="px-3 py-1.5 rounded-xl bg-amber-50 text-amber-900 border border-amber-200 hover:bg-amber-100">📋 Start.io লাইন কপি</button>
          </div>
        </div>
        <div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-2.5 text-xs">
          <div class="p-3 rounded-2xl border ${cfg.adTestMode ? "bg-red-50 border-red-200 text-red-900" : "bg-emerald-50 border-emerald-200 text-emerald-900"}">
            <div class="font-bold">${cfg.adTestMode ? "⚠️ টেস্ট মোড চালু" : "✅ লাইভ প্রোডাকশন মোড"}</div>
            <div class="text-[11px] opacity-80">${cfg.adTestMode ? "রিলিজের আগে টেস্ট মোড বন্ধ করুন" : "Test Mode OFF (রিলিজের জন্য প্রস্তুত)"}</div>
          </div>
          <div class="p-3 rounded-2xl border bg-emerald-50 border-emerald-200 text-emerald-900">
            <div class="font-bold">🔑 ${isLevelPlayActive ? "LevelPlay Mediation" : "Direct Unity 6227313"}</div>
            <div class="text-[11px] opacity-80">${isLevelPlayActive ? "Admin App Key সক্রিয় (অথবা Android Secret)" : "Android Secret বা Admin Key দিয়ে LevelPlay সক্রিয় হয়"}</div>
          </div>
          <div class="p-3 rounded-2xl border ${hasStartIoId || cfg.startIoEnabled ? "bg-emerald-50 border-emerald-200 text-emerald-900" : "bg-sky-50 border-sky-200 text-sky-900"}">
            <div class="font-bold">🚀 Start.io (SDK 5.4.0)</div>
            <div class="text-[11px] opacity-80">${hasStartIoId ? "Admin ID সক্রিয়" : "AI Studio Secret (STARTIO_APP_ID) সমর্থিত"}</div>
          </div>
          <div class="p-3 rounded-2xl border bg-emerald-50 border-emerald-200 text-emerald-900">
            <div class="font-bold">📄 Start.io app-ads.txt</div>
            <div class="text-[11px] opacity-80">start.io, 165433365, DIRECT হোস্টেড</div>
          </div>
        </div>
      </div>

      <form onsubmit="window.kbApp.handleAdminSaveAdConfig(event)" class="space-y-5">
        <div class="bg-emerald-50 border border-emerald-200 rounded-3xl p-5 flex flex-wrap items-center justify-between gap-3">
          <div class="space-y-1">
            <h3 class="text-lg font-bold text-[#105B34]">💰 Direct Unity Ads (Game ID: 6227313) + LevelPlay (১০ নেটওয়ার্ক)</h3>
            <p class="text-xs text-gray-700">Android অ্যাপ থেকে AdMob সম্পূর্ণভাবে অপসারণ করা হয়েছে। Unity Ads Game ID <strong>6227313</strong> (Banner_Android, Interstitial_Android, Rewarded_Android) সরাসরি সক্রিয় থাকে; LevelPlay App Key ও Start.io App ID AI Studio Secrets অথবা এখান থেকে চালাতে পারবেন।</p>
          </div>
          <div class="flex flex-wrap gap-2 text-xs font-bold">
            <button type="button" onclick="window.kbApp.applyUnityDefaultPreset()" class="px-3 py-2 rounded-xl bg-[#105B34] text-white shadow hover:bg-[#168447]">⚡ Unity Ads 6227313 ডিফল্ট সেট</button>
            <label class="flex items-center gap-2 bg-white px-3 py-2 rounded-xl border border-emerald-200"><input type="checkbox" name="adsEnabled" ${cfg.adsEnabled ? "checked" : ""} /> মাস্টার বিজ্ঞাপন চালু</label>
            <label class="flex items-center gap-2 bg-white px-3 py-2 rounded-xl border border-emerald-200"><input type="checkbox" name="showAdsLabel" ${cfg.showAdsLabel ? "checked" : ""} /> Sponsored ব্যাজ</label>
            <label class="flex items-center gap-2 bg-white px-3 py-2 rounded-xl border border-amber-200"><input type="checkbox" name="adTestMode" ${cfg.adTestMode ? "checked" : ""} /> টেস্ট মোড</label>
          </div>
        </div>

        <div class="grid md:grid-cols-2 gap-5">
          <!-- Direct Unity Ads & LevelPlay Mediation & 5 Ad Formats -->
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">1️⃣ Android Direct Unity Ads ও LevelPlay ৫ ফরম্যাট</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="androidAdsEnabled" ${cfg.androidAdsEnabled ? "checked" : ""} /> Android বিজ্ঞাপন চালু</label>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Unity LevelPlay App Key (ঐচ্ছিক — ফাঁকা থাকলে Direct Unity 6227313 চলবে)</label>
              <input name="mediationAppKey" value="${escapeHtml(cfg.mediationAppKey || "")}" placeholder="যেমন: 85460dcd (LevelPlay ব্যবহার করলে দিন)" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div class="grid grid-cols-2 gap-2.5 pt-1 text-xs">
              <div class="p-2.5 rounded-xl bg-[#F7FAF7] border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1.5"><input type="checkbox" name="bannerEnabled" ${cfg.bannerEnabled !== false ? "checked" : ""} /> Adaptive Banner</label>
                <input name="androidBannerId" value="${escapeHtml(cfg.androidBannerId || "Banner_Android")}" placeholder="Banner_Android" class="w-full px-2.5 py-1.5 rounded-lg border border-emerald-200 text-xs" />
              </div>
              <div class="p-2.5 rounded-xl bg-[#F7FAF7] border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1.5"><input type="checkbox" name="nativeEnabled" ${cfg.nativeEnabled !== false ? "checked" : ""} /> Native Ad</label>
                <input name="androidNativePlacement" value="${escapeHtml(cfg.androidNativePlacement || "")}" placeholder="Native Placement Name" class="w-full px-2.5 py-1.5 rounded-lg border border-emerald-200 text-xs" />
              </div>
              <div class="p-2.5 rounded-xl bg-[#F7FAF7] border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1.5"><input type="checkbox" name="interstitialEnabled" ${cfg.interstitialEnabled !== false ? "checked" : ""} /> Interstitial</label>
                <input name="androidInterstitialId" value="${escapeHtml(cfg.androidInterstitialId || "Interstitial_Android")}" placeholder="Interstitial_Android" class="w-full px-2.5 py-1.5 rounded-lg border border-emerald-200 text-xs" />
              </div>
              <div class="p-2.5 rounded-xl bg-[#F7FAF7] border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1.5"><input type="checkbox" name="rewardedEnabled" ${cfg.rewardedEnabled !== false ? "checked" : ""} /> Rewarded Video</label>
                <input name="androidRewardedId" value="${escapeHtml(cfg.androidRewardedId || "Rewarded_Android")}" placeholder="Rewarded_Android" class="w-full px-2.5 py-1.5 rounded-lg border border-emerald-200 text-xs" />
              </div>
            </div>
            <div class="p-2.5 rounded-xl bg-[#F7FAF7] border border-emerald-100 space-y-1 text-xs">
              <label class="font-bold flex items-center gap-1.5"><input type="checkbox" name="appOpenEnabled" ${cfg.appOpenEnabled ? "checked" : ""} /> App Open Ad</label>
              <input name="androidAppOpenId" value="${escapeHtml(cfg.androidAppOpenId || "")}" placeholder="App Open Ad Unit / Placement" class="w-full px-2.5 py-1.5 rounded-lg border border-emerald-200 text-xs" />
            </div>
          </div>

          <!-- 8 Individual Android Mediation Networks -->
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-2.5">
            <h4 class="font-bold text-[#105B34]">2️⃣ ৮টি মিডিয়েশন নেটওয়ার্ক নিয়ন্ত্রণ (AdMob-মুক্ত)</h4>
            <p class="text-[11px] text-amber-800 bg-amber-50 p-2 rounded-xl border border-amber-200">ℹ️ শুধু <strong>Unity Ads (6227313)</strong> সরাসরি প্রস্তুত; বাকি ৭টির (AppLovin MAX, Meta, Mintegral, Pangle, InMobi, Liftoff, DT Exchange) জন্য নিজস্ব পাবলিশার অ্যাকাউন্ট ও অনুমোদন আবশ্যক।</p>
            <div class="grid grid-cols-2 gap-2 text-xs">
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="unityAdsEnabled" ${cfg.unityAdsEnabled !== false ? "checked" : ""} /> Unity Ads (প্রস্তুত)</label>
                <input name="unityGameId" value="${escapeHtml(cfg.unityGameId || "6227313")}" placeholder="6227313" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="appLovinEnabled" ${cfg.appLovinEnabled ? "checked" : ""} /> AppLovin MAX</label>
                <input name="appLovinSdkKey" value="${escapeHtml(cfg.appLovinSdkKey || "")}" placeholder="AppLovin SDK Key" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="metaAudienceEnabled" ${cfg.metaAudienceEnabled ? "checked" : ""} /> Meta Audience</label>
                <input name="metaPlacementId" value="${escapeHtml(cfg.metaPlacementId || "")}" placeholder="Meta Placement/App ID" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="mintegralEnabled" ${cfg.mintegralEnabled ? "checked" : ""} /> Mintegral</label>
                <input name="mintegralAppId" value="${escapeHtml(cfg.mintegralAppId || "")}" placeholder="Mintegral App ID" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="pangleEnabled" ${cfg.pangleEnabled ? "checked" : ""} /> Pangle</label>
                <input name="pangleAppId" value="${escapeHtml(cfg.pangleAppId || "")}" placeholder="Pangle App ID" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="inMobiEnabled" ${cfg.inMobiEnabled ? "checked" : ""} /> InMobi</label>
                <input name="inMobiAccountId" value="${escapeHtml(cfg.inMobiAccountId || "")}" placeholder="InMobi Account ID" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="liftoffEnabled" ${cfg.liftoffEnabled ? "checked" : ""} /> Liftoff Monetize</label>
                <input name="liftoffAppId" value="${escapeHtml(cfg.liftoffAppId || "")}" placeholder="Liftoff App ID" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="dtExchangeEnabled" ${cfg.dtExchangeEnabled ? "checked" : ""} /> DT Exchange</label>
                <input name="dtExchangeAppId" value="${escapeHtml(cfg.dtExchangeAppId || "")}" placeholder="DT Exchange App ID" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="chartboostEnabled" ${cfg.chartboostEnabled ? "checked" : ""} /> Chartboost</label>
                <input name="chartboostAppId" value="${escapeHtml(cfg.chartboostAppId || "")}" placeholder="Chartboost App ID" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
              <div class="p-2 rounded-xl border border-emerald-100 space-y-1">
                <label class="font-bold flex items-center gap-1"><input type="checkbox" name="startIoEnabled" ${cfg.startIoEnabled ? "checked" : ""} /> Start.io (Direct)</label>
                <input name="startIoAppId" value="${escapeHtml(cfg.startIoAppId || "")}" placeholder="Start.io App ID" class="w-full px-2 py-1 rounded-lg border border-emerald-200" />
              </div>
            </div>
            <label class="text-xs font-bold flex items-center gap-1.5 bg-emerald-50 px-3 py-2 rounded-xl border border-emerald-200"><input type="checkbox" name="consentGranted" ${cfg.consentGranted !== false ? "checked" : ""} /> ব্যবহারকারী বিজ্ঞাপন সম্মতি (GDPR / Privacy Consent)</label>
          </div>

          <!-- Google AdSense & Hybrid Web Fallback -->
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">3️⃣ Google AdSense ও মোবাইল ওয়েব ব্যাকআপ</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="adsenseEnabled" ${cfg.adsenseEnabled ? "checked" : ""} /> AdSense সক্রিয়</label>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Publisher Client ID (ca-pub-...)</label>
              <input name="adsenseClient" value="${escapeHtml(cfg.adsenseClient)}" placeholder="ca-pub-xxxxxxxxxxxxxxxx" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div class="grid grid-cols-3 gap-2">
              <div><label class="text-[11px] font-bold block mb-1">Top Slot</label><input name="adsenseTopSlot" value="${escapeHtml(cfg.adsenseTopSlot)}" placeholder="1234567890" class="w-full px-2.5 py-2 rounded-xl border border-emerald-200 text-xs" /></div>
              <div><label class="text-[11px] font-bold block mb-1">Feed Slot</label><input name="adsenseFeedSlot" value="${escapeHtml(cfg.adsenseFeedSlot)}" placeholder="2345678901" class="w-full px-2.5 py-2 rounded-xl border border-emerald-200 text-xs" /></div>
              <div><label class="text-[11px] font-bold block mb-1">Footer Slot</label><input name="adsenseFooterSlot" value="${escapeHtml(cfg.adsenseFooterSlot)}" placeholder="3456789012" class="w-full px-2.5 py-2 rounded-xl border border-emerald-200 text-xs" /></div>
            </div>
            <label class="text-xs font-bold flex items-center gap-1.5 bg-[#EAF7EF] px-3 py-2 rounded-xl border border-emerald-200"><input type="checkbox" name="mobileWebAdsEnabled" ${cfg.mobileWebAdsEnabled !== false ? "checked" : ""} /> মোবাইলে Hybrid Web Fallback চালু</label>
            <div>
              <label class="text-xs font-bold block mb-1">অগ্রাধিকার নেটওয়ার্ক</label>
              <select name="mobileAdNetworkPriority" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs bg-white">
                <option value="ALL" ${(cfg.mobileAdNetworkPriority || "ALL") === "ALL" ? "selected" : ""}>সব সক্রিয় নেটওয়ার্ক (Unity Direct / LevelPlay + Web)</option>
                <option value="UNITY_DIRECT" ${cfg.mobileAdNetworkPriority === "UNITY_DIRECT" ? "selected" : ""}>Direct Unity Ads (6227313)</option>
                <option value="UNITY_LEVELPLAY" ${cfg.mobileAdNetworkPriority === "UNITY_LEVELPLAY" ? "selected" : ""}>Unity LevelPlay Mediation</option>
                <option value="ADSTERRA" ${cfg.mobileAdNetworkPriority === "ADSTERRA" ? "selected" : ""}>Adsterra</option>
                <option value="MONETAG" ${cfg.mobileAdNetworkPriority === "MONETAG" ? "selected" : ""}>Monetag / PropellerAds</option>
                <option value="ADSENSE" ${cfg.mobileAdNetworkPriority === "ADSENSE" ? "selected" : ""}>Google AdSense (Web)</option>
                <option value="CUSTOM" ${cfg.mobileAdNetworkPriority === "CUSTOM" ? "selected" : ""}>Custom HTML</option>
                <option value="SPONSOR" ${cfg.mobileAdNetworkPriority === "SPONSOR" ? "selected" : ""}>সরাসরি কৃষি স্পন্সর</option>
              </select>
            </div>
            <input name="mobileDirectLinkUrl" value="${escapeHtml(cfg.mobileDirectLinkUrl || "")}" placeholder="Direct Link / SmartLink URL (https://...)" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            <textarea name="mobileCustomAdHtml" rows="2" placeholder="মোবাইল Custom HTML5 ট্যাগ" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs font-mono">${escapeHtml(cfg.mobileCustomAdHtml || "")}</textarea>
          </div>

          <!-- Adsterra & Monetag -->
          <div class="space-y-4">
            <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
              <div class="flex items-center justify-between">
                <h4 class="font-bold text-[#105B34]">4️⃣ Adsterra Ad Network</h4>
                <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="adsterraEnabled" ${cfg.adsterraEnabled ? "checked" : ""} /> সক্রিয়</label>
              </div>
              <input name="adsterraBannerKey" value="${escapeHtml(cfg.adsterraBannerKey)}" placeholder="32-char Adsterra key" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
              <input name="adsterraScriptUrl" value="${escapeHtml(cfg.adsterraScriptUrl)}" placeholder="Banner invoke.js URL" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
              <input name="adsterraSocialBarUrl" value="${escapeHtml(cfg.adsterraSocialBarUrl)}" placeholder="Social Bar / Popunder Script URL" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
              <div class="flex items-center justify-between">
                <h4 class="font-bold text-[#105B34]">5️⃣ Monetag / PropellerAds</h4>
                <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="monetagEnabled" ${cfg.monetagEnabled ? "checked" : ""} /> সক্রিয়</label>
              </div>
              <input name="monetagZoneId" value="${escapeHtml(cfg.monetagZoneId)}" placeholder="Zone ID (e.g. 8123456)" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
              <input name="monetagScriptUrl" value="${escapeHtml(cfg.monetagScriptUrl)}" placeholder="https://alwingulla.com/88/tag.min.js" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
          </div>
        </div>

        <!-- Custom HTML & Direct Sponsor -->
        <div class="grid md:grid-cols-2 gap-5">
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">6️⃣ Ezoic / Media.net / Custom HTML</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="customAdEnabled" ${cfg.customAdEnabled ? "checked" : ""} /> সক্রিয়</label>
            </div>
            <input name="customAdName" value="${escapeHtml(cfg.customAdName)}" placeholder="Network Name" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            <textarea name="customTopHtml" rows="2" placeholder="Top Banner HTML" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs font-mono">${escapeHtml(cfg.customTopHtml)}</textarea>
            <textarea name="customFeedHtml" rows="2" placeholder="In-Feed Banner HTML" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs font-mono">${escapeHtml(cfg.customFeedHtml)}</textarea>
            <textarea name="customFooterHtml" rows="2" placeholder="Footer Banner HTML" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs font-mono">${escapeHtml(cfg.customFooterHtml)}</textarea>
          </div>

          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">7️⃣ সরাসরি কৃষি স্পন্সর ব্যানার</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="sponsorEnabled" ${cfg.sponsorEnabled ? "checked" : ""} /> সক্রিয়</label>
            </div>
            <input name="sponsorTitle" value="${escapeHtml(cfg.sponsorTitle)}" placeholder="স্পন্সর শিরোনাম" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            <input name="sponsorSubtitle" value="${escapeHtml(cfg.sponsorSubtitle)}" placeholder="সংক্ষিপ্ত বিবরণ" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            <input name="sponsorImageUrl" value="${escapeHtml(cfg.sponsorImageUrl)}" placeholder="ব্যানার ছবি URL" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            <div class="grid grid-cols-2 gap-2">
              <input name="sponsorTargetUrl" value="${escapeHtml(cfg.sponsorTargetUrl)}" placeholder="লিংক URL" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
              <input name="sponsorCtaText" value="${escapeHtml(cfg.sponsorCtaText)}" placeholder="বাটন টেক্সট" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
          </div>
        </div>

        <button type="submit" class="w-full py-3.5 rounded-2xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm shadow">
          💾 সব বিজ্ঞাপন ও LevelPlay সেটিংস সেভ করুন (অ্যাপ ও ওয়েবসাইটে লাইভ হবে)
        </button>
      </form>
    `;
  } else if (tab === "tips") {
    const activeTipsCount = state.dailyTips.filter((t) => t.isActive !== false).length;
    bodyHtml = `
      <div class="grid lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm space-y-4">
          <h3 class="text-lg font-bold text-[#105B34]">💡 নতুন ডেইলি কৃষি টিপস যোগ করুন</h3>
          <p class="text-xs text-gray-600">এখান থেকে যোগ, সম্পাদনা বা মুছে ফেলা ডেইলি টিপস তাৎক্ষণিকভাবে Android অ্যাপের Rewarded Video কার্ডে ও ওয়েবসাইটে লাইভ হবে।</p>
          <form onsubmit="window.kbApp.handleAdminAddDailyTip(event)" class="space-y-3">
            <div class="grid grid-cols-3 gap-2">
              <div>
                <label class="text-xs font-bold block mb-1">আইকন (Emoji)</label>
                <select name="emoji" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
                  <option value="🌾">🌾 ধান/শস্য</option>
                  <option value="🧅">🧅 সবজি</option>
                  <option value="📦">📦 পরিবহন</option>
                  <option value="🚜">🚜 চাষাবাদ</option>
                  <option value="🌱">🌱 চারা/বীজ</option>
                  <option value="💧">💧 সেচ</option>
                  <option value="🌦️">🌦️ আবহাওয়া</option>
                  <option value="💰">💰 বাজার দর</option>
                </select>
              </div>
              <div class="col-span-2">
                <label class="text-xs font-bold block mb-1">ক্যাটাগরি</label>
                <input name="categoryBn" value="পাইকারি পরামর্শ" placeholder="পাইকারি পরামর্শ / সংরক্ষণ" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm" />
              </div>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">শিরোনাম *</label>
              <input name="titleBn" required placeholder="যেমন: ধান ও ভুট্টা সংরক্ষণ পরামর্শ" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">বিস্তারিত ডেইলি টিপস *</label>
              <textarea name="contentBn" rows="3" required placeholder="কৃষক ও ক্রেতাদের জন্য উপকারী টিপস লিখুন..." class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm"></textarea>
            </div>
            <label class="flex items-center gap-2 text-xs font-bold bg-[#EAF7EF] px-3 py-2.5 rounded-xl border border-emerald-200">
              <input type="checkbox" name="isActive" checked /> সক্রিয় রাখুন (Rewarded Video ও হোমপেজে দেখাবে)
            </label>
            <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">+ ডেইলি টিপস যোগ করুন</button>
          </form>
        </div>

        <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm space-y-3">
          <div class="flex items-center justify-between">
            <h3 class="text-base font-bold text-[#105B34]">সব ডেইলি টিপস (${state.dailyTips.length}টি • সক্রিয় ${activeTipsCount}টি)</h3>
          </div>
          <div class="space-y-3 max-h-[540px] overflow-y-auto">
            ${
              !state.dailyTips.length
                ? `<div class="p-8 text-center text-xs text-gray-500 border border-dashed border-emerald-200 rounded-2xl">কোনো ডেইলি টিপস নেই। বাম পাশের ফর্ম থেকে যোগ করুন।</div>`
                : state.dailyTips
                    .map(
                      (t) => `<div class="p-4 rounded-2xl border ${t.isActive !== false ? "border-emerald-200 bg-[#F7FAF7]" : "border-gray-200 bg-gray-50"} space-y-2 text-xs">
                        <div class="flex items-start justify-between gap-2">
                          <div class="font-bold text-sm text-[#105B34]">${escapeHtml(t.emoji || "🌾")} ${escapeHtml(t.titleBn)}</div>
                          <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${t.isActive !== false ? "bg-emerald-100 text-emerald-800" : "bg-gray-200 text-gray-700"}">${t.isActive !== false ? "সক্রিয়" : "বন্ধ"}</span>
                        </div>
                        <div class="text-[11px] font-semibold text-[#168447]">${escapeHtml(t.categoryBn || "কৃষি পরামর্শ")}</div>
                        <p class="text-gray-700 leading-relaxed">${escapeHtml(t.contentBn)}</p>
                        <div class="flex flex-wrap gap-2 pt-1">
                          <button type="button" onclick="window.kbApp.toggleDailyTipActive('${escapeHtml(t.id)}')" class="px-3 py-1 rounded-lg border border-emerald-200 bg-white text-[#105B34] font-bold">${t.isActive !== false ? "⏸ বন্ধ করুন" : "▶ চালু করুন"}</button>
                          <button type="button" onclick="window.kbApp.openAdminEditDailyTipModal('${escapeHtml(t.id)}')" class="px-3 py-1 rounded-lg bg-[#EAF7EF] text-[#105B34] font-bold border border-emerald-200">✏️ সম্পাদনা</button>
                          <button type="button" onclick="window.kbApp.confirmDeleteDailyTip('${escapeHtml(t.id)}')" class="px-3 py-1 rounded-lg bg-red-600 text-white font-bold">🗑️ মুছুন</button>
                        </div>
                      </div>`
                    )
                    .join("")
            }
          </div>
        </div>
      </div>
    `;
  } else if (tab === "sliders") {
    const activeSlidersCount = state.heroSliders.filter((s) => s.isActive !== false).length;
    bodyHtml = `
      <div class="grid lg:grid-cols-2 gap-6">
        <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm space-y-4">
          <h3 class="text-lg font-bold text-[#105B34]">🖼️ নতুন হিরো স্লাইডার যোগ করুন</h3>
          <p class="text-xs text-gray-600">হোম স্ক্রিনের প্রধান ব্যানার ক্যারousel এখান থেকে যোগ, সম্পাদনা বা মুছতে পারবেন (অ্যাপ ও ওয়েবসাইটে লাইভ সিঙ্ক হয়)।</p>
          <form onsubmit="window.kbApp.handleAdminAddSlider(event)" class="space-y-3">
            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="text-xs font-bold block mb-1">ব্যাজ টেক্সট</label>
                <input name="badgeBn" value="১০০% দালালমুক্ত বাজার" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
              </div>
              <div>
                <label class="text-xs font-bold block mb-1">স্লাইড ক্রম (Sort Order)</label>
                <input name="sortOrder" type="number" min="1" value="${state.heroSliders.length + 1}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
              </div>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">মূল শিরোনাম *</label>
              <input name="titleBn" required placeholder="যেমন: সরাসরি কৃষকের কাছ থেকে" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">হাইলাইট উপশিরোনাম</label>
              <input name="subtitleBn" placeholder="যেমন: তাজা ফসল কিনুন" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
            </div>
            <div class="grid grid-cols-3 gap-2">
              <input name="bullet1Bn" value="কৃষকের ন্যায্য দাম" placeholder="পয়েন্ট ১" class="w-full px-2.5 py-2 rounded-xl border border-emerald-200 text-xs" />
              <input name="bullet2Bn" value="ক্রেতার ন্যায্য দামে ক্রয়" placeholder="পয়েন্ট ২" class="w-full px-2.5 py-2 rounded-xl border border-emerald-200 text-xs" />
              <input name="bullet3Bn" value="সরাসরি যোগাযোগ" placeholder="পয়েন্ট ৩" class="w-full px-2.5 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="text-xs font-bold block mb-1">বাটন টেক্সট</label>
                <input name="ctaTextBn" value="এখনই অর্ডার করুন" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
              </div>
              <div>
                <label class="text-xs font-bold block mb-1">লক্ষ্য পাতা (Route)</label>
                <select name="targetRoute" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs bg-white">
                  <option value="browse">ফসল খুঁজুন (browse)</option>
                  <option value="farmers">কৃষক খুঁজুন (farmers)</option>
                  <option value="sell">ফসল বিক্রি (sell)</option>
                  <option value="activation">অ্যাক্টিভেশন / Ad-Free</option>
                  <option value="register_farmer">কৃষক নিবন্ধন</option>
                </select>
              </div>
            </div>
            <div class="space-y-2">
              <label class="text-xs font-bold block">ব্যানার ছবি URL বা গ্যালারি থেকে আপলোড</label>
              <input name="imageUri" id="add-slider-img-uri" placeholder="https://... অথবা নিচে ছবি আপলোড করুন" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
              <label class="inline-flex cursor-pointer px-3.5 py-2 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-[#105B34] text-xs font-bold hover:bg-emerald-100">
                🖼️ ব্যানার ছবি আপলোড
                <input type="file" accept="image/*" onchange="window.kbApp.handleCompressedImageUpload(this, 'add-slider-img-uri', 'add-slider-img-preview')" class="hidden" />
              </label>
              <img id="add-slider-img-preview" class="hidden max-h-36 rounded-2xl border border-emerald-200 object-cover" alt="" />
            </div>
            <label class="flex items-center gap-2 text-xs font-bold bg-[#EAF7EF] px-3 py-2 rounded-xl border border-emerald-200">
              <input type="checkbox" name="isActive" checked /> সক্রিয় রাখুন
            </label>
            <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">+ হিরো স্লাইডার যোগ করুন</button>
          </form>
        </div>

        <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm space-y-3">
          <h3 class="text-base font-bold text-[#105B34]">সব হিরো স্লাইডার (${state.heroSliders.length}টি • সক্রিয় ${activeSlidersCount}টি)</h3>
          <div class="space-y-3 max-h-[600px] overflow-y-auto">
            ${
              !state.heroSliders.length
                ? `<div class="p-8 text-center text-xs text-gray-500 border border-dashed border-emerald-200 rounded-2xl">কোনো কাস্টম স্লাইডার নেই। ডিফল্ট ফ্ল্যাগশিপ স্লাইড দেখাচ্ছে।</div>`
                : state.heroSliders
                    .map(
                      (s) => `<div class="p-4 rounded-2xl border ${s.isActive !== false ? "border-emerald-200 bg-[#F7FAF7]" : "border-gray-200 bg-gray-50"} space-y-2 text-xs">
                        <div class="flex items-start justify-between gap-2">
                          <div class="flex items-center gap-2.5">
                            ${s.imageUri ? `<img src="${escapeHtml(s.imageUri)}" alt="" class="w-16 h-11 rounded-xl object-cover border border-emerald-200" />` : `<div class="w-16 h-11 rounded-xl bg-[#EAF7EF] flex items-center justify-center">🖼️</div>`}
                            <div>
                              <div class="font-bold text-sm text-[#105B34]">#${s.sortOrder || 1} • ${escapeHtml(s.titleBn)}</div>
                              ${s.subtitleBn ? `<div class="text-amber-700 font-semibold">${escapeHtml(s.subtitleBn)}</div>` : ""}
                            </div>
                          </div>
                          <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${s.isActive !== false ? "bg-emerald-100 text-emerald-800" : "bg-gray-200 text-gray-700"}">${s.isActive !== false ? "সক্রিয়" : "বন্ধ"}</span>
                        </div>
                        <div class="text-[11px] text-gray-600">বাটন: ${escapeHtml(s.ctaTextBn)} → ${escapeHtml(s.targetRoute)}</div>
                        <div class="flex flex-wrap gap-2 pt-1">
                          <button type="button" onclick="window.kbApp.toggleHeroSliderActive('${escapeHtml(s.id)}')" class="px-3 py-1 rounded-lg border border-emerald-200 bg-white text-[#105B34] font-bold">${s.isActive !== false ? "⏸ বন্ধ করুন" : "▶ চালু করুন"}</button>
                          <button type="button" onclick="window.kbApp.openAdminEditSliderModal('${escapeHtml(s.id)}')" class="px-3 py-1 rounded-lg bg-[#EAF7EF] text-[#105B34] font-bold border border-emerald-200">✏️ সম্পাদনা</button>
                          <button type="button" onclick="window.kbApp.confirmDeleteHeroSlider('${escapeHtml(s.id)}')" class="px-3 py-1 rounded-lg bg-red-600 text-white font-bold">🗑️ মুছুন</button>
                        </div>
                      </div>`
                    )
                    .join("")
            }
          </div>
        </div>
      </div>
    `;
  } else {
    bodyHtml = `<div class="space-y-2">${state.auditLogs.slice(0, 60).map((a) => `<div class="bg-white rounded-xl p-3 border border-emerald-100 text-xs flex justify-between"><div><strong>${escapeHtml(a.actionBn)}</strong> — ${escapeHtml(a.detailsBn)}</div><span class="text-gray-500">${new Date(a.createdAt).toLocaleString("bn-BD")}</span></div>`).join("") || `<div class="bg-white rounded-2xl p-8 text-center">কোনো লগ নেই।</div>`}</div>`;
  }

  return `
    <div class="space-y-5">
      <div class="bg-amber-50 border border-amber-200 rounded-3xl p-5 flex flex-wrap justify-between items-center gap-3">
        <div>
          <h1 class="text-2xl font-bold text-[#105B34]">🛡️ সুপার অ্যাডমিন ড্যাশবোর্ড</h1>
          <p class="text-xs text-gray-700">সারা বাংলাদেশ (৬৪ জেলা) প্ল্যাটফর্ম তদারকি, কৃষক ও ক্রেতা যাচাই, সব অ্যাড নেটওয়ার্ক ও লাইভ নোটিফিকেশন নিয়ন্ত্রণ</p>
        </div>
      </div>

      <div class="flex flex-wrap gap-2">
        ${tabs.map(([id, label]) => `<button onclick="window.kbApp.setAdminTab('${id}')" class="px-3.5 py-2 rounded-xl text-xs font-bold ${tab === id ? "bg-[#105B34] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">${label}</button>`).join("")}
      </div>

      ${bodyHtml}
    </div>
  `;
}

function renderInfoPage() {
  const tab = state.infoTab;
  const content = {
    about: `<h2 class="text-xl font-bold text-[#105B34]">আমাদের সম্পর্কে</h2><p class="text-sm text-gray-700 leading-relaxed">কৃষি বন্ধু (Krishi Bondhu) হলো বাংলাদেশের কৃষকদের ন্যায্য মূল্য নিশ্চিত করার জন্য নির্মিত একটি ১০০% দালালমুক্ত, শূন্য-কমিশন ডিজিটাল কৃষিপণ্য বাজার। কুষ্টিয়া জেলা থেকে সূচনা হয়ে এখন সারা বাংলাদেশের ৮টি বিভাগ ও ৬৪টি জেলায় কৃষক ও ক্রেতার মধ্যে সরাসরি সংযোগ স্থাপন করছে।</p>`,
    contact: `<h2 class="text-xl font-bold text-[#105B34]">যোগাযোগ</h2><p class="text-sm text-gray-700">সহায়তা কেন্দ্র: support@krishibondhu.bd • হেল্পলাইন: +৮৮০ ১৭০০-০০০০০০ • সারা বাংলাদেশ (৬৪ জেলা)</p>`,
    privacy: `<h2 class="text-xl font-bold text-[#105B34]">গোপনীয়তা নীতি (Privacy Policy)</h2><p class="text-sm text-gray-700 leading-relaxed">ব্যবহারকারীদের পাসওয়ার্ড SHA-256 হ্যাশ দিয়ে সুরক্ষিত রাখা হয় এবং প্রতিটি নোটিফিকেশন শুধুমাত্র নির্দিষ্ট প্রাপক বা অনুমোদিত ঘোষণার মধ্যেই সীমাবদ্ধ থাকে।</p><p class="text-sm text-gray-700 leading-relaxed mt-2">বিজ্ঞাপন ও মনিটাইজেশন: মোবাইল অ্যাপে Unity LevelPlay mediation (Unity Ads, AppLovin, Meta, Mintegral, Pangle, InMobi, Liftoff, DT Exchange) এবং ওয়েবসাইটে Google AdSense, Adsterra, Monetag বা অনুমোদিত বিজ্ঞাপন নেটওয়ার্ক ব্যবহার হতে পারে। প্রাসঙ্গিক বিজ্ঞাপন দেখাতে কুকি বা Advertising ID ব্যবহৃত হতে পারে, তবে আপনার এনআইডি বা পাসওয়ার্ড কখনোই বিজ্ঞাপনদাতার সাথে শেয়ার করা হয় না।</p><p class="text-xs text-gray-500 mt-2">ভার্সন ${APP_VERSION_NAME} • বিল্ড ${APP_BUILD_VERSION}</p>`,
    terms: `<h2 class="text-xl font-bold text-[#105B34]">শর্তাবলী</h2><p class="text-sm text-gray-700 leading-relaxed">কৃষক নিজেই ফসলের দাম ও মান নির্ধারণ করেন। প্ল্যাটফর্ম কোনো দালাল বা বাধ্যতামূলক লেনদেন কমিশন গ্রহণ করে না। অ্যাপ ও ওয়েবসাইটের রক্ষণাবেক্ষণের জন্য নির্ধারিত স্থানে বিজ্ঞাপন প্রদর্শিত হতে পারে।</p>`
  };

  return `
    <div class="max-w-3xl mx-auto space-y-5">
      <div class="flex flex-wrap gap-2">
        <button onclick="window.kbApp.setInfoTab('about')" class="px-4 py-2 rounded-xl text-xs font-bold ${tab === "about" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200"}">আমাদের সম্পর্কে</button>
        <button onclick="window.kbApp.setInfoTab('contact')" class="px-4 py-2 rounded-xl text-xs font-bold ${tab === "contact" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200"}">যোগাযোগ</button>
        <button onclick="window.kbApp.setInfoTab('privacy')" class="px-4 py-2 rounded-xl text-xs font-bold ${tab === "privacy" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200"}">গোপনীয়তা নীতি</button>
        <button onclick="window.kbApp.setInfoTab('terms')" class="px-4 py-2 rounded-xl text-xs font-bold ${tab === "terms" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200"}">শর্তাবলী</button>
      </div>
      <div class="bg-white rounded-3xl p-6 sm:p-8 border border-emerald-100 shadow-sm space-y-3">${content[tab] || content.about}</div>
    </div>
  `;
}

// ---------------------- Master Render ----------------------

function renderCurrentView() {
  updateHeaderChrome();
  const root = document.getElementById("app-root");
  if (!root) return;
  switch (state.page) {
    case "home":
      root.innerHTML = renderHomePage();
      break;
    case "browse":
      root.innerHTML = renderBrowsePage();
      break;
    case "product-detail":
      root.innerHTML = renderProductDetailPage();
      break;
    case "farmers":
      root.innerHTML = renderFarmersPage();
      break;
    case "farmer-profile":
      root.innerHTML = renderFarmerPublicProfilePage();
      break;
    case "auth-login":
      root.innerHTML = renderLoginPage();
      break;
    case "auth-register-farmer":
      root.innerHTML = renderRegisterPage("FARMER");
      break;
    case "auth-register-buyer":
      root.innerHTML = renderRegisterPage("BUYER");
      break;
    case "profile":
      root.innerHTML = renderProfilePage();
      break;
    case "activation":
      root.innerHTML = renderActivationPage();
      break;
    case "farmer-dashboard":
      root.innerHTML = renderFarmerDashboardPage();
      break;
    case "cart":
      root.innerHTML = renderCartPage();
      break;
    case "orders":
      root.innerHTML = renderOrdersPage();
      break;
    case "notifications":
      root.innerHTML = renderNotificationsPage();
      break;
    case "admin":
      root.innerHTML = renderAdminDashboardPage();
      break;
    case "info":
      root.innerHTML = renderInfoPage();
      break;
    default:
      root.innerHTML = renderHomePage();
  }
  mountPageAdPlacements();
}

// ---------------------- Event Bridge ----------------------

window.kbApp = {
  navigate,
  logout,
  toggleLanguage() {
    state.lang = state.lang === "bn" ? "en" : "bn";
    saveLocalSnapshot();
    renderCurrentView();
  },
  goSell() {
    const user = getActiveUser();
    if (user && user.role === "FARMER") {
      navigate("farmer-dashboard");
    } else if (user && user.role === "ADMIN") {
      navigate("admin");
    } else {
      navigate("auth-register-farmer");
    }
  },
  applyHomeSearch() {
    const el = document.getElementById("home-search-input");
    state.filters.query = el ? el.value : "";
    navigate("browse");
  },
  filterByCategory(catId) {
    state.filters.categoryId = catId;
    navigate("browse");
  },
  setFilter(key, val) {
    state.filters[key] = val;
    if (key === "division") {
      state.filters.district = "";
      state.filters.upazila = "";
      state.filters.union = "";
    }
    if (key === "district") {
      state.filters.upazila = "";
      state.filters.union = "";
    }
    if (key === "upazila") {
      state.filters.union = "";
    }
    renderCurrentView();
  },
  resetFilters() {
    state.filters = { query: "", categoryId: "", division: "", district: "", upazila: "", union: "", sort: "NEWEST" };
    renderCurrentView();
  },
  applyMyAreaFilter() {
    const user = getActiveUser();
    if (!user || !user.district) {
      showToast("আপনার এলাকার তথ্য প্রোফাইলে সংরক্ষণ করুন।", "info");
      return;
    }
    state.filters.division = user.division || "";
    state.filters.district = user.district || "";
    state.filters.upazila = user.upazila || "";
    state.filters.union = user.unionName || "";
    renderCurrentView();
    showToast(`🏡 আপনার এলাকা: ${user.upazila}, ${user.district}`, "success");
  },
  requireContactGate() {
    const user = getActiveUser();
    if (!user) {
      showToast("কৃষকের সাথে যোগাযোগ করতে লগইন ও অ্যাকাউন্ট সক্রিয় করুন।", "info");
      navigate("auth-login");
      return;
    }
    showToast("যোগাযোগ নম্বর দেখতে আগে আপনার অ্যাকাউন্ট সক্রিয় (৳৩০০) করুন।", "info");
    navigate("activation");
  },
  onLocationCascadeChange(prefix, divBn, distBn) {
    const divSel = document.getElementById(`${prefix}-division`);
    const distSel = document.getElementById(`${prefix}-district`);
    const upzSel = document.getElementById(`${prefix}-upazila`);
    if (divBn !== null && divBn !== undefined) {
      const dists = BD_DISTRICTS.filter((d) => d.divisionBn === divBn);
      if (distSel) {
        distSel.innerHTML = dists.map((d) => `<option value="${escapeHtml(d.districtBn)}">${escapeHtml(d.districtBn)}</option>`).join("");
      }
      if (upzSel && dists[0]) {
        upzSel.innerHTML = dists[0].upazilas.map((u) => `<option value="${escapeHtml(u)}">${escapeHtml(u)}</option>`).join("");
      }
    } else if (distBn !== null && distBn !== undefined) {
      const d = BD_DISTRICTS.find((x) => x.districtBn === distBn);
      if (upzSel && d) {
        upzSel.innerHTML = d.upazilas.map((u) => `<option value="${escapeHtml(u)}">${escapeHtml(u)}</option>`).join("");
      }
    } else if (divSel) {
      window.kbApp.onLocationCascadeChange(prefix, divSel.value, null);
    }
  },
  onRegDivisionChange(divBn) {
    window.kbApp.onLocationCascadeChange("reg", divBn, null);
  },
  onRegDistrictChange(distBn) {
    window.kbApp.onLocationCascadeChange("reg", null, distBn);
  },
  togglePasswordVisibility(inputId, btn) {
    const inp = document.getElementById(inputId);
    if (!inp) return;
    const isHidden = inp.type === "password";
    inp.type = isHidden ? "text" : "password";
    if (btn) btn.textContent = isHidden ? "লুকান" : "দেখুন";
  },
  openForgotPasswordModal() {
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <h3 class="text-lg font-bold text-[#105B34]">🔑 পাসওয়ার্ড রিসেট (OTP ছাড়াই)</h3>
          <button type="button" onclick="window.kbApp.closeModal()" class="text-gray-500 hover:text-black font-bold px-2">✕</button>
        </div>
        <p class="text-xs text-gray-600">আপনার নিবন্ধিত মোবাইল নম্বর দিয়ে সরাসরি নতুন পাসওয়ার্ড সেট করুন (অ্যাপ ও ওয়েবসাইটে সাথে সাথে কাজ করবে)।</p>
        <form onsubmit="window.kbApp.handleForgotPasswordSubmit(event)" class="space-y-3 text-sm">
          <div>
            <label class="text-xs font-bold block mb-1">নিবন্ধিত মোবাইল নম্বর *</label>
            <input name="phone" required placeholder="017XXXXXXXX" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">নতুন পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর) *</label>
            <div class="relative">
              <input id="fp-new-pass" name="newPassword" type="password" minlength="6" required class="w-full px-3.5 py-2.5 pr-16 rounded-xl border border-emerald-200" />
              <button type="button" onclick="window.kbApp.togglePasswordVisibility('fp-new-pass', this)" class="absolute right-2 top-1/2 -translate-y-1/2 px-2 py-1 text-xs font-bold text-[#168447] rounded-lg">দেখুন</button>
            </div>
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">পাসওয়ার্ড নিশ্চিত করুন *</label>
            <div class="relative">
              <input id="fp-confirm-pass" name="confirmPassword" type="password" minlength="6" required class="w-full px-3.5 py-2.5 pr-16 rounded-xl border border-emerald-200" />
              <button type="button" onclick="window.kbApp.togglePasswordVisibility('fp-confirm-pass', this)" class="absolute right-2 top-1/2 -translate-y-1/2 px-2 py-1 text-xs font-bold text-[#168447] rounded-lg">দেখুন</button>
            </div>
          </div>
          <div class="flex gap-2 justify-end pt-1">
            <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
            <button type="submit" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">পাসওয়ার্ড পরিবর্তন করুন</button>
          </div>
        </form>
      </div>
    `);
  },
  async handleForgotPasswordSubmit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await resetPassword(fd.get("phone"), fd.get("newPassword"), fd.get("confirmPassword"));
  },
  async handleLoginSubmit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await login(fd.get("identifier"), fd.get("password"));
  },
  async handleRegisterSubmit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await registerUser(Object.fromEntries(fd.entries()));
  },
  async handleProfileUpdateSubmit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await updateUserProfile(Object.fromEntries(fd.entries()));
  },
  async handleFarmerPaymentSettingsSubmit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await saveFarmerPaymentSettings({
      bKashNumber: fd.get("bKashNumber"),
      nagadNumber: fd.get("nagadNumber"),
      rocketNumber: fd.get("rocketNumber"),
      bankAccountInfo: fd.get("bankAccountInfo")
    });
  },
  async handleActivationPaymentSubmit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await submitActivationPayment({
      paymentMethod: fd.get("paymentMethod"),
      senderMobile: fd.get("senderMobile"),
      transactionId: fd.get("transactionId"),
      amount: fd.get("amount"),
      receiptUri: fd.get("receiptUri"),
      isRenewal: fd.get("isRenewal") === "1",
      isAdRemoval: fd.get("isAdRemoval") === "1"
    });
  },
  handleCompressedImageUpload(input, hiddenInputId, previewImgId, fallbackId) {
    const file = input.files && input.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const maxDim = 512;
        const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(img.width * scale));
        canvas.height = Math.max(1, Math.round(img.height * scale));
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        const dataUrl = canvas.toDataURL("image/jpeg", 0.7);
        const hidden = document.getElementById(hiddenInputId);
        if (hidden) hidden.value = dataUrl;
        const preview = document.getElementById(previewImgId);
        if (preview) {
          preview.src = dataUrl;
          preview.classList.remove("hidden");
        }
        if (fallbackId) {
          const fb = document.getElementById(fallbackId);
          if (fb) fb.classList.add("hidden");
        }
        showToast("ছবি সংকুচিত ও প্রস্তুত হয়েছে!", "success");
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  },
  copyText(text) {
    if (typeof navigator !== "undefined" && navigator.clipboard) {
      navigator.clipboard.writeText(String(text || "")).catch(() => {});
    }
    showToast(`কপি হয়েছে: ${text}`, "success");
  },
  handleProductImageUpload(input) {
    const file = input.files && input.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const maxDim = 512;
        const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(img.width * scale));
        canvas.height = Math.max(1, Math.round(img.height * scale));
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        const dataUrl = canvas.toDataURL("image/jpeg", 0.7);
        const hidden = document.getElementById("new-prod-image-uri");
        if (hidden) hidden.value = dataUrl;
        const preview = document.getElementById("new-prod-image-preview");
        if (preview) {
          preview.src = dataUrl;
          preview.classList.remove("hidden");
        }
        showToast("ছবি সংকুচিত ও প্রস্তুত হয়েছে!", "success");
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  },
  applyResolvedLocation(targetContext, loc) {
    if (!loc) return;
    if (targetContext === "browse" || targetContext === "farmers") {
      state.filters.division = loc.divisionBn;
      state.filters.district = loc.districtBn;
      state.filters.upazila = "";
      renderCurrentView();
    } else if (targetContext === "register") {
      const divSel = document.getElementById("reg-division");
      const distSel = document.getElementById("reg-district");
      const upzSel = document.getElementById("reg-upazila");
      if (divSel) {
        divSel.value = loc.divisionBn;
        window.kbApp.onRegDivisionChange(loc.divisionBn);
      }
      if (distSel) {
        distSel.value = loc.districtBn;
        window.kbApp.onRegDistrictChange(loc.districtBn);
      }
      if (upzSel && loc.upazilaBn) {
        upzSel.value = loc.upazilaBn;
      }
      const statusEl = document.getElementById("reg-loc-status");
      if (statusEl) {
        statusEl.textContent = `✓ ম্যাপ/GPS থেকে পাওয়া: ${loc.upazilaBn}, ${loc.districtBn} (${loc.divisionBn})`;
      }
    } else if (targetContext === "pickup") {
      const el = document.getElementById("new-prod-pickup-address");
      if (el) el.value = `${loc.upazilaBn}, ${loc.districtBn}, ${loc.divisionBn}`;
    } else if (targetContext === "checkout") {
      const el = document.getElementById("checkout-delivery-address");
      if (el) el.value = loc.addressLine || `${loc.upazilaBn}, ${loc.districtBn}, ${loc.divisionBn}`;
    }
    showToast(`📍 ${loc.upazilaBn}, ${loc.districtBn} (${loc.divisionBn}) নির্ধারিত হয়েছে!`, "success");
  },
  async detectBrowserLocation(targetContext) {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      showToast("ব্রাউজারে GPS সুবিধা পাওয়া যায়নি; ম্যাপ থেকে জেলা বেছে নিন।", "info");
      window.kbApp.openLocationMapModal(targetContext);
      return;
    }
    showToast("GPS থেকে বর্তমান অবস্থান খোঁজা হচ্ছে...", "info");
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        let rawAddress = "";
        try {
          const resp = await fetch(
            `https://nominatim.openstreetmap.org/reverse?format=json&lat=${encodeURIComponent(lat)}&lon=${encodeURIComponent(lng)}&accept-language=bn,en`
          );
          if (resp.ok) {
            const data = await resp.json();
            rawAddress = data.display_name || "";
          }
        } catch (_) {
          // Offline / CORS fallback uses 64-district nearest centroid matching automatically
        }
        const resolved = resolveLocationFromCoords(lat, lng, rawAddress);
        window.kbApp.applyResolvedLocation(targetContext, resolved);
      },
      () => {
        showToast("GPS অনুমতি পাওয়া যায়নি। ম্যাপ থেকে আপনার জেলা বেছে নিন।", "info");
        window.kbApp.openLocationMapModal(targetContext);
      },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 }
    );
  },
  openLocationMapModal(targetContext, selectedDistrict = "কুষ্টিয়া") {
    const selCentroid = BD_DISTRICT_CENTROIDS.find((c) => c.districtBn === selectedDistrict) || BD_DISTRICT_CENTROIDS.find((c) => c.districtBn === "কুষ্টিয়া");
    const minLng = 88.0, maxLng = 92.7, minLat = 20.6, maxLat = 26.65;
    const toSvg = (lat, lng) => {
      const x = Math.round(((lng - minLng) / (maxLng - minLng)) * 300 + 20);
      const y = Math.round(((maxLat - lat) / (maxLat - minLat)) * 340 + 20);
      return { x, y };
    };
    const pinsHtml = BD_DISTRICT_CENTROIDS.map((c) => {
      const { x, y } = toSvg(c.lat, c.lng);
      const isSel = c.districtBn === selCentroid.districtBn;
      return `
        <g onclick="window.kbApp.openLocationMapModal('${escapeHtml(targetContext)}', '${escapeHtml(c.districtBn)}')" style="cursor:pointer">
          <circle cx="${x}" cy="${y}" r="${isSel ? 8 : 4.5}" fill="${isSel ? "#D97706" : "#168447"}" stroke="#FFFFFF" stroke-width="${isSel ? 2.5 : 1.2}" />
          ${isSel ? `<text x="${Math.min(285, Math.max(45, x))}" y="${Math.max(16, y - 11)}" text-anchor="middle" font-size="11" font-weight="bold" fill="#105B34">${escapeHtml(c.districtBn)}</text>` : ""}
        </g>
      `;
    }).join("");

    openModal(`
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <h3 class="text-lg font-bold text-[#105B34]">🗺️ বাংলাদেশের ম্যাপ থেকে এলাকা নির্বাচন</h3>
          <button type="button" onclick="window.kbApp.closeModal()" class="text-gray-500 hover:text-black font-bold px-2">✕</button>
        </div>
        <p class="text-xs text-gray-600">ম্যাপের যেকোনো বিন্দু বা নিচের ৬৪ জেলার তালিকায় ক্লিক করে অটো ঠিকানা সেট করুন।</p>
        <div class="bg-[#F7FAF7] rounded-2xl border border-emerald-200 p-2 flex flex-col items-center">
          <svg viewBox="0 0 340 380" class="w-full max-w-[300px] h-auto" onclick="window.kbApp.handleSvgMapClick(event, '${escapeHtml(targetContext)}')">
            <rect x="0" y="0" width="340" height="380" rx="16" fill="#EAF7EF" />
            <polygon points="105,22 150,35 162,72 225,92 272,102 252,155 215,180 232,250 268,350 235,342 212,265 168,260 128,288 85,270 72,202 35,148 52,95 88,58" fill="#FFFFFF" stroke="#168447" stroke-width="2" />
            ${pinsHtml}
          </svg>
        </div>
        <div class="bg-[#EAF7EF] rounded-2xl p-3 flex items-center justify-between text-xs">
          <div>
            <div class="font-bold text-[#105B34]">নির্বাচিত: ${escapeHtml(selCentroid.districtBn)} (${escapeHtml(selCentroid.divisionBn)} বিভাগ)</div>
            <div class="text-gray-600">অক্ষাংশ: ${selCentroid.lat.toFixed(3)}, দ্রাঘিমাংশ: ${selCentroid.lng.toFixed(3)}</div>
          </div>
          <a href="https://www.openstreetmap.org/?mlat=${selCentroid.lat}&mlon=${selCentroid.lng}#map=10/${selCentroid.lat}/${selCentroid.lng}" target="_blank" rel="noopener" class="px-2.5 py-1.5 rounded-lg bg-white border border-emerald-200 text-[#105B34] font-bold">ম্যাপ লিংক ↗</a>
        </div>
        <select onchange="window.kbApp.openLocationMapModal('${escapeHtml(targetContext)}', this.value)" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm bg-white">
          ${BD_DISTRICT_CENTROIDS.map((c) => `<option value="${escapeHtml(c.districtBn)}" ${c.districtBn === selCentroid.districtBn ? "selected" : ""}>${escapeHtml(c.districtBn)} (${escapeHtml(c.divisionBn)})</option>`).join("")}
        </select>
        <div class="flex gap-2">
          <button type="button" onclick="window.kbApp.closeModal(); window.kbApp.detectBrowserLocation('${escapeHtml(targetContext)}');" class="flex-1 py-2.5 rounded-xl border border-emerald-200 text-[#105B34] text-xs font-bold">📍 GPS অটো ডিটেক্ট</button>
          <button type="button" onclick="window.kbApp.confirmWebMapLocation('${escapeHtml(targetContext)}', '${escapeHtml(selCentroid.districtBn)}')" class="flex-1 py-2.5 rounded-xl bg-[#168447] text-white text-xs font-bold">✓ এই এলাকা নিশ্চিত করুন</button>
        </div>
      </div>
    `);
  },
  handleSvgMapClick(event, targetContext) {
    if (event.target.tagName === "circle") return;
    const svg = event.currentTarget;
    const rect = svg.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * 340;
    const y = ((event.clientY - rect.top) / rect.height) * 380;
    const lng = 88.0 + ((x - 20) / 300) * (92.7 - 88.0);
    const lat = 26.65 - ((y - 20) / 340) * (26.65 - 20.6);
    const resolved = resolveLocationFromCoords(lat, lng, "");
    window.kbApp.openLocationMapModal(targetContext, resolved.districtBn);
  },
  confirmWebMapLocation(targetContext, districtBn) {
    const c = BD_DISTRICT_CENTROIDS.find((x) => x.districtBn === districtBn) || BD_DISTRICT_CENTROIDS[0];
    const resolved = resolveLocationFromCoords(c.lat, c.lng, "");
    closeModal();
    window.kbApp.applyResolvedLocation(targetContext, resolved);
  },
  async handleCreateProductSubmit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await createFarmerProduct({
      nameBn: fd.get("nameBn"),
      categoryId: fd.get("categoryId"),
      quantity: fd.get("quantity"),
      unit: fd.get("unit"),
      pricePerUnit: fd.get("pricePerUnit"),
      descriptionBn: fd.get("descriptionBn"),
      imageUri: fd.get("imageUri"),
      pickupAddress: fd.get("pickupAddress"),
      supportsFarmPickup: fd.get("supportsFarmPickup") === "on",
      supportsDirectDelivery: fd.get("supportsDirectDelivery") === "on",
      isWholesale: true,
      deliveryCharge: fd.get("deliveryCharge"),
      harvestDate: fd.get("harvestDate")
    });
  },
  addToCart,
  updateCartQty,
  updateCartAdvancePreview(grandTotal) {
    const inp = document.getElementById("checkout-advance-input");
    const out = document.getElementById("checkout-remaining-due");
    if (!inp || !out) return;
    const raw = Number(inp.value || 0);
    const capped = Math.max(0, Math.min(Number(grandTotal || 0), Number.isNaN(raw) ? 0 : raw));
    const rem = Math.max(0, Number(grandTotal || 0) - capped);
    out.textContent = formatBdt(rem);
  },
  async handleCheckoutSubmit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await placeOrdersFromCart(Object.fromEntries(fd.entries()));
  },
  updateAddProductPreview() {
    const qty = Number(document.getElementById("add-prod-qty")?.value || 0);
    const price = Number(document.getElementById("add-prod-price")?.value || 0);
    const preview = document.getElementById("add-prod-preview");
    if (!preview) return;
    const kg = Math.max(0, qty) * MON_TO_KG;
    const total = Math.max(0, qty) * Math.max(0, price);
    preview.innerHTML = `<span>মোট ওজন: ${formatQty(kg)} কেজি</span><span>মোট পাইকারি মূল্য: ${formatBdt(total)}</span>`;
  },
  updateFarmerPriceAndStock,
  updateOrderStatus,
  farmerVerifyOrderPayment,
  farmerApproveZeroAdvanceOrder,
  farmerConfirmPaymentReceived,
  adminApproveActivationPayment,
  adminSetAccountStatus,
  adminSetUserAdsDisabled,
  dispatchHeroRoute,
  setHeroSlide(idx) {
    state.heroSlideIdx = Math.max(0, Number(idx || 0));
    renderCurrentView();
  },
  prevHeroSlide(total) {
    const n = Math.max(1, Number(total || 1));
    state.heroSlideIdx = (Number(state.heroSlideIdx || 0) - 1 + n) % n;
    renderCurrentView();
  },
  nextHeroSlide(total) {
    const n = Math.max(1, Number(total || 1));
    state.heroSlideIdx = (Number(state.heroSlideIdx || 0) + 1) % n;
    renderCurrentView();
  },
  setActivationMode(mode) {
    state.activationMode = mode === "ad_free" ? "ad_free" : "account";
    renderCurrentView();
  },
  async handleAdminAddSlider(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const ok = await adminSaveHeroSlider({
      badgeBn: fd.get("badgeBn"),
      titleBn: fd.get("titleBn"),
      subtitleBn: fd.get("subtitleBn"),
      bullet1Bn: fd.get("bullet1Bn"),
      bullet2Bn: fd.get("bullet2Bn"),
      bullet3Bn: fd.get("bullet3Bn"),
      ctaTextBn: fd.get("ctaTextBn"),
      targetRoute: fd.get("targetRoute"),
      imageUri: fd.get("imageUri"),
      sortOrder: fd.get("sortOrder"),
      isActive: fd.get("isActive") === "on"
    });
    if (ok) e.target.reset();
  },
  async toggleHeroSliderActive(sliderId) {
    const s = state.heroSliders.find((x) => x.id === sliderId);
    if (!s) return;
    await adminSaveHeroSlider({ ...s, isActive: s.isActive === false });
  },
  openAdminEditSliderModal(sliderId) {
    const s = state.heroSliders.find((x) => x.id === sliderId);
    if (!s) return;
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-3">✏️ হিরো স্লাইডার সম্পাদনা</h3>
      <form onsubmit="window.kbApp.handleAdminEditSliderSubmit(event, '${escapeHtml(s.id)}')" class="space-y-2.5 text-xs">
        <div class="grid grid-cols-2 gap-2">
          <div>
            <label class="font-bold block mb-1">ব্যাজ টেক্সট</label>
            <input name="badgeBn" value="${escapeHtml(s.badgeBn)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200" />
          </div>
          <div>
            <label class="font-bold block mb-1">ক্রম (Sort Order)</label>
            <input name="sortOrder" type="number" min="1" value="${s.sortOrder || 1}" class="w-full px-3 py-2 rounded-xl border border-emerald-200" />
          </div>
        </div>
        <div>
          <label class="font-bold block mb-1">মূল শিরোনাম *</label>
          <input name="titleBn" required value="${escapeHtml(s.titleBn)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
        </div>
        <div>
          <label class="font-bold block mb-1">হাইলাইট উপশিরোনাম</label>
          <input name="subtitleBn" value="${escapeHtml(s.subtitleBn)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200" />
        </div>
        <div class="grid grid-cols-3 gap-2">
          <input name="bullet1Bn" value="${escapeHtml(s.bullet1Bn)}" class="w-full px-2.5 py-2 rounded-xl border border-emerald-200" />
          <input name="bullet2Bn" value="${escapeHtml(s.bullet2Bn)}" class="w-full px-2.5 py-2 rounded-xl border border-emerald-200" />
          <input name="bullet3Bn" value="${escapeHtml(s.bullet3Bn)}" class="w-full px-2.5 py-2 rounded-xl border border-emerald-200" />
        </div>
        <div class="grid grid-cols-2 gap-2">
          <input name="ctaTextBn" value="${escapeHtml(s.ctaTextBn)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200" />
          <select name="targetRoute" class="w-full px-3 py-2 rounded-xl border border-emerald-200 bg-white">
            <option value="browse" ${s.targetRoute === "browse" ? "selected" : ""}>ফসল খুঁজুন</option>
            <option value="farmers" ${s.targetRoute === "farmers" ? "selected" : ""}>কৃষক খুঁজুন</option>
            <option value="sell" ${s.targetRoute === "sell" ? "selected" : ""}>ফসল বিক্রি</option>
            <option value="activation" ${s.targetRoute === "activation" ? "selected" : ""}>অ্যাক্টিভেশন / Ad-Free</option>
            <option value="register_farmer" ${s.targetRoute === "register_farmer" ? "selected" : ""}>কৃষক নিবন্ধন</option>
          </select>
        </div>
        <div>
          <label class="font-bold block mb-1">ছবি URL</label>
          <input name="imageUri" id="edit-slider-img-uri" value="${escapeHtml(s.imageUri)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200" />
        </div>
        <label class="flex items-center gap-2 font-bold">
          <input type="checkbox" name="isActive" ${s.isActive !== false ? "checked" : ""} /> সক্রিয় রাখুন
        </label>
        <div class="flex gap-2 justify-end pt-1">
          <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border font-bold">বাতিল</button>
          <button type="submit" class="px-4 py-2 rounded-xl bg-[#168447] text-white font-bold">সংরক্ষণ</button>
        </div>
      </form>
    `);
  },
  async handleAdminEditSliderSubmit(e, sliderId) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const ok = await adminSaveHeroSlider({
      id: sliderId,
      badgeBn: fd.get("badgeBn"),
      titleBn: fd.get("titleBn"),
      subtitleBn: fd.get("subtitleBn"),
      bullet1Bn: fd.get("bullet1Bn"),
      bullet2Bn: fd.get("bullet2Bn"),
      bullet3Bn: fd.get("bullet3Bn"),
      ctaTextBn: fd.get("ctaTextBn"),
      targetRoute: fd.get("targetRoute"),
      imageUri: fd.get("imageUri"),
      sortOrder: fd.get("sortOrder"),
      isActive: fd.get("isActive") === "on"
    });
    if (ok) closeModal();
  },
  confirmDeleteHeroSlider(sliderId) {
    const s = state.heroSliders.find((x) => x.id === sliderId);
    if (!s) return;
    openModal(`
      <h3 class="text-lg font-bold text-red-700 mb-2">🗑️ হিরো স্লাইডার মুছবেন?</h3>
      <p class="text-sm text-gray-700 mb-4">"${escapeHtml(s.titleBn)}" স্থায়ীভাবে মুছে ফেলা হবে।</p>
      <div class="flex gap-2 justify-end">
        <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
        <button type="button" onclick="window.kbApp.deleteHeroSliderConfirmed('${escapeHtml(sliderId)}')" class="px-4 py-2 rounded-xl bg-red-600 text-white text-xs font-bold">হ্যাঁ, মুছুন</button>
      </div>
    `);
  },
  async deleteHeroSliderConfirmed(sliderId) {
    closeModal();
    await adminDeleteHeroSlider(sliderId);
  },
  openRejectOrderPaymentModal(paymentId) {
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-3">✕ অগ্রিম পেমেন্ট প্রত্যাখ্যান করুন</h3>
      <form onsubmit="window.kbApp.handleRejectOrderPaymentSubmit(event, '${escapeHtml(paymentId)}')" class="space-y-3">
        <div>
          <label class="text-xs font-bold block mb-1">প্রত্যাখ্যানের কারণ *</label>
          <textarea name="reason" rows="3" required placeholder="যেমন: অ্যাকাউন্ট ব্যালেন্সে টাকা পাওয়া যায়নি বা TxID সঠিক নয়।" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm"></textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
          <button type="submit" class="px-4 py-2 rounded-xl bg-red-600 text-white text-xs font-bold">প্রত্যাখ্যান নিশ্চিত</button>
        </div>
      </form>
    `);
  },
  async handleRejectOrderPaymentSubmit(e, paymentId) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await farmerVerifyOrderPayment(paymentId, false, fd.get("reason"));
  },
  setFarmerTab(t) { state.farmerTab = t; renderCurrentView(); },
  setAdminTab(t) { state.adminTab = t; renderCurrentView(); },
  setAdminUserFilter(f) { state.adminUserFilter = f; renderCurrentView(); },
  setAdminUserStatusFilter(s) { state.adminUserStatusFilter = s; renderCurrentView(); },
  setAdminUserSearch(q) { state.adminUserSearch = q; renderCurrentView(); },
  setAdminPaymentFilter(f) { state.adminPaymentFilter = f; renderCurrentView(); },
  setAdminPaymentQuery(q) { state.adminPaymentQuery = q; renderCurrentView(); },
  setAdminOrderFilter(f) { state.adminOrderFilter = f; renderCurrentView(); },
  setAdminProductFilter(f) { state.adminProductFilter = f; renderCurrentView(); },
  setAdminProductSearch(q) { state.adminProductSearch = q; renderCurrentView(); },
  setInfoTab(t) { state.infoTab = t; renderCurrentView(); },
  adminVerifyUser,
  adminVerifyFarmer: adminVerifyUser,
  adminVerifyProduct,
  openAdminRejectProductModal(productId) {
    const prod = state.products.find((p) => p.id === productId);
    if (!prod) return;
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-3">✕ ফসল প্রত্যাখ্যান করুন (${escapeHtml(prod.nameBn)})</h3>
      <form onsubmit="window.kbApp.handleAdminRejectProductSubmit(event, '${escapeHtml(productId)}')" class="space-y-3">
        <div>
          <label class="text-xs font-bold block mb-1">প্রত্যাখ্যানের কারণ *</label>
          <textarea name="reason" rows="3" required placeholder="যেমন: পণ্যের দাম বা বিবরণ সঠিক নয়।" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm"></textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
          <button type="submit" class="px-4 py-2 rounded-xl bg-red-600 text-white text-xs font-bold">প্রত্যাখ্যান নিশ্চিত</button>
        </div>
      </form>
    `);
  },
  async handleAdminRejectProductSubmit(e, productId) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await adminVerifyProduct(productId, false, fd.get("reason"));
  },
  openAdminEditProductModal(productId) {
    const prod = state.products.find((p) => p.id === productId);
    if (!prod) return;
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-3">✏️ ফসলের তথ্য সম্পাদনা করুন</h3>
      <form onsubmit="window.kbApp.handleAdminEditProductSubmit(event, '${escapeHtml(productId)}')" class="space-y-3">
        <div>
          <label class="text-xs font-bold block mb-1">ফসলের নাম *</label>
          <input name="nameBn" required value="${escapeHtml(prod.nameBn)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
        </div>
        <div>
          <label class="text-xs font-bold block mb-1">ক্যাটাগরি *</label>
          <select name="categoryId" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm bg-white">
            ${state.categories.map((c) => `<option value="${escapeHtml(c.id)}" ${c.id === prod.categoryId ? "selected" : ""}>${escapeHtml(c.emoji || "")} ${escapeHtml(c.nameBn)}</option>`).join("")}
          </select>
        </div>
        <div class="grid grid-cols-2 gap-2">
          <div>
            <label class="text-xs font-bold block mb-1">প্রতি মণের দাম (৳) *</label>
            <input name="pricePerUnit" type="number" min="1" step="0.5" required value="${prod.pricePerUnit}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm font-bold" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">মজুত (মণ) *</label>
            <input name="availableQuantity" type="number" min="0" step="0.5" required value="${prod.availableQuantity}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm font-bold" />
          </div>
        </div>
        <div>
          <label class="text-xs font-bold block mb-1">অবস্থা (Status)</label>
          <select name="status" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm bg-white">
            <option value="ACTIVE" ${prod.status === "ACTIVE" ? "selected" : ""}>অনুমোদিত ও প্রকাশিত (ACTIVE)</option>
            <option value="PENDING_APPROVAL" ${prod.status === "PENDING_APPROVAL" ? "selected" : ""}>যাচাই অপেক্ষমাণ (PENDING_APPROVAL)</option>
            <option value="REJECTED" ${prod.status === "REJECTED" ? "selected" : ""}>প্রত্যাখ্যাত (REJECTED)</option>
            <option value="OUT_OF_STOCK" ${prod.status === "OUT_OF_STOCK" ? "selected" : ""}>মজুত শেষ (OUT_OF_STOCK)</option>
          </select>
        </div>
        <div>
          <label class="text-xs font-bold block mb-1">বিবরণ</label>
          <textarea name="descriptionBn" rows="2" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm">${escapeHtml(prod.descriptionBn || "")}</textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
          <button type="submit" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">আপডেট সংরক্ষণ</button>
        </div>
      </form>
    `);
  },
  async handleAdminEditProductSubmit(e, productId) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await adminUpdateProduct(productId, Object.fromEntries(fd.entries()));
  },
  confirmDeleteProduct(productId) {
    const prod = state.products.find((p) => p.id === productId);
    if (!prod) return;
    openModal(`
      <h3 class="text-lg font-bold text-red-700 mb-2">🗑️ ফসলটি স্থায়ীভাবে মুছবেন?</h3>
      <p class="text-sm text-gray-700 mb-4">"${escapeHtml(prod.nameBn)}" স্থায়ীভাবে ডাটাবেস থেকে মুছে ফেলা হবে।</p>
      <div class="flex gap-2 justify-end">
        <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
        <button type="button" onclick="window.kbApp.deleteProductConfirmed('${escapeHtml(productId)}')" class="px-4 py-2 rounded-xl bg-red-600 text-white text-xs font-bold">হ্যাঁ, মুছুন</button>
      </div>
    `);
  },
  async deleteProductConfirmed(productId) {
    await deleteProduct(productId);
  },
  async applyUnityDefaultPreset() {
    await adminSaveAdConfig({
      ...(state.adConfig || DEFAULT_AD_CONFIG),
      adsEnabled: true,
      androidAdsEnabled: true,
      mediationPlatform: "UNITY_DIRECT",
      mediationAppKey: "",
      unityAdsEnabled: true,
      unityGameId: "6227313",
      bannerEnabled: true,
      androidBannerId: "Banner_Android",
      interstitialEnabled: true,
      androidInterstitialId: "Interstitial_Android",
      rewardedEnabled: true,
      androidRewardedId: "Rewarded_Android",
      adTestMode: false
    });
  },
  revealNextDailyTip() {
    const activeTips = state.dailyTips.filter((t) => t.isActive !== false);
    if (!activeTips.length) return;
    state.dailyTipIndex = (state.dailyTipIndex + 1) % activeTips.length;
    state.revealedDailyTipId = activeTips[state.dailyTipIndex].id;
    renderCurrentView();
  },
  async handleAdminAddDailyTip(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const ok = await adminSaveDailyTip({
      emoji: String(fd.get("emoji") || "🌾"),
      categoryBn: String(fd.get("categoryBn") || "কৃষি পরামর্শ"),
      titleBn: String(fd.get("titleBn") || ""),
      contentBn: String(fd.get("contentBn") || ""),
      isActive: fd.get("isActive") === "on"
    });
    if (ok) e.target.reset();
  },
  async toggleDailyTipActive(tipId) {
    const tip = state.dailyTips.find((t) => t.id === tipId);
    if (!tip) return;
    await adminSaveDailyTip({ ...tip, isActive: tip.isActive === false });
  },
  openAdminEditDailyTipModal(tipId) {
    const tip = state.dailyTips.find((t) => t.id === tipId);
    if (!tip) return;
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-3">✏️ ডেইলি টিপস সম্পাদনা</h3>
      <form onsubmit="window.kbApp.handleAdminEditDailyTipSubmit(event, '${escapeHtml(tip.id)}')" class="space-y-3">
        <div class="grid grid-cols-3 gap-2">
          <div>
            <label class="text-xs font-bold block mb-1">আইকন</label>
            <input name="emoji" value="${escapeHtml(tip.emoji || "🌾")}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div class="col-span-2">
            <label class="text-xs font-bold block mb-1">ক্যাটাগরি</label>
            <input name="categoryBn" value="${escapeHtml(tip.categoryBn || "কৃষি পরামর্শ")}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          </div>
        </div>
        <div>
          <label class="text-xs font-bold block mb-1">শিরোনাম *</label>
          <input name="titleBn" required value="${escapeHtml(tip.titleBn)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
        </div>
        <div>
          <label class="text-xs font-bold block mb-1">বিস্তারিত টিপস *</label>
          <textarea name="contentBn" rows="3" required class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm">${escapeHtml(tip.contentBn)}</textarea>
        </div>
        <label class="flex items-center gap-2 text-xs font-bold">
          <input type="checkbox" name="isActive" ${tip.isActive !== false ? "checked" : ""} /> সক্রিয় রাখুন
        </label>
        <div class="flex gap-2 justify-end">
          <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
          <button type="submit" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">সংরক্ষণ</button>
        </div>
      </form>
    `);
  },
  async handleAdminEditDailyTipSubmit(e, tipId) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const ok = await adminSaveDailyTip({
      id: tipId,
      emoji: String(fd.get("emoji") || "🌾"),
      categoryBn: String(fd.get("categoryBn") || "কৃষি পরামর্শ"),
      titleBn: String(fd.get("titleBn") || ""),
      contentBn: String(fd.get("contentBn") || ""),
      isActive: fd.get("isActive") === "on"
    });
    if (ok) closeModal();
  },
  confirmDeleteDailyTip(tipId) {
    const tip = state.dailyTips.find((t) => t.id === tipId);
    if (!tip) return;
    openModal(`
      <h3 class="text-lg font-bold text-red-700 mb-2">🗑️ ডেইলি টিপস মুছবেন?</h3>
      <p class="text-sm text-gray-700 mb-4">"${escapeHtml(tip.titleBn)}" স্থায়ীভাবে মুছে ফেলা হবে।</p>
      <div class="flex gap-2 justify-end">
        <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
        <button type="button" onclick="window.kbApp.deleteDailyTipConfirmed('${escapeHtml(tipId)}')" class="px-4 py-2 rounded-xl bg-red-600 text-white text-xs font-bold">হ্যাঁ, মুছুন</button>
      </div>
    `);
  },
  async deleteDailyTipConfirmed(tipId) {
    closeModal();
    await adminDeleteDailyTip(tipId);
  },
  adminResolveComplaint,
  exportCsvReport,
  markVisibleNotificationsRead,
  openReceiptPreviewModal(id, kind = "activation") {
    const item =
      kind === "order"
        ? state.orders.find((o) => o.id === id)
        : state.activationPayments.find((p) => p.id === id);
    const src = kind === "order" ? item?.paymentReceiptUri : item?.receiptUri;
    if (!src) {
      showToast("কোনো রসিদ ছবি পাওয়া যায়নি।", "info");
      return;
    }
    openModal(`
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <h3 class="text-base font-bold text-[#105B34]">🖼️ পেমেন্ট রসিদ প্রিভিউ</h3>
          <button type="button" onclick="window.kbApp.closeModal()" class="text-gray-500 hover:text-black font-bold px-2">✕</button>
        </div>
        <img src="${escapeHtml(src)}" alt="পেমেন্ট রসিদ" class="w-full max-h-[65vh] object-contain rounded-2xl border border-emerald-200 bg-[#F7FAF7]" />
      </div>
    `);
  },
  openRejectActivationModal(paymentId, requestResubmission = false) {
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-3">${requestResubmission ? "↻ পুনঃজমার অনুরোধ পাঠান" : "✕ পেমেন্ট প্রত্যাখ্যান করুন"}</h3>
      <form onsubmit="window.kbApp.handleRejectActivationSubmit(event, '${escapeHtml(paymentId)}', ${requestResubmission ? "true" : "false"})" class="space-y-3">
        <div>
          <label class="text-xs font-bold block mb-1">কারণ / অ্যাডমিন নোট *</label>
          <textarea name="reason" rows="3" required placeholder="${requestResubmission ? "যেমন: TxID অস্পষ্ট, সঠিক TxID ও স্ক্রিনশট দিন।" : "প্রত্যাখ্যানের কারণ লিখুন..."}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm"></textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
          <button type="submit" class="px-4 py-2 rounded-xl ${requestResubmission ? "bg-amber-600" : "bg-red-600"} text-white text-xs font-bold">${requestResubmission ? "অনুরোধ পাঠান" : "প্রত্যাখ্যান নিশ্চিত"}</button>
        </div>
      </form>
    `);
  },
  async handleRejectActivationSubmit(e, paymentId, requestResubmission) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await adminRejectActivationPayment(paymentId, fd.get("reason"), Boolean(requestResubmission));
  },
  openOrderPaymentModal(orderId) {
    const order = state.orders.find((o) => o.id === orderId);
    if (!order) return;
    const remainingDue = Math.max(0, Number(order.grandTotal || 0) - Number(order.verifiedAdvanceTotal || 0));
    const fps = getFarmerPaymentSettings(order.farmerId);
    const channels = [
      fps.bKashNumber ? `bKash: ${fps.bKashNumber}` : "",
      fps.nagadNumber ? `Nagad: ${fps.nagadNumber}` : "",
      fps.rocketNumber ? `Rocket: ${fps.rocketNumber}` : "",
      fps.bankAccountInfo ? `Bank: ${fps.bankAccountInfo}` : ""
    ].filter(Boolean);
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-2">💳 অগ্রিম / আংশিক পেমেন্ট জমা (${escapeHtml(order.orderCode)})</h3>
      <p class="text-xs text-gray-600 mb-3">মোট: <strong>${formatBdt(order.grandTotal)}</strong> • যাচাইকৃত অগ্রিম: <strong>${formatBdt(order.verifiedAdvanceTotal || 0)}</strong> • অবশিষ্ট বকেয়া: <strong class="text-amber-800">${formatBdt(remainingDue)}</strong></p>
      <div class="bg-[#EAF7EF] rounded-2xl p-3 text-[11px] text-[#105B34] mb-3">
        কৃষক <strong>${escapeHtml(order.farmerName)}</strong>-এর সরাসরি নম্বর: ${channels.length ? escapeHtml(channels.join(" • ")) : "নম্বর সেট করা হয়নি"}
      </div>
      <form onsubmit="window.kbApp.handleOrderPaymentSubmit(event, '${escapeHtml(order.id)}')" class="space-y-3">
        <div class="grid grid-cols-2 gap-2">
          <div>
            <label class="text-xs font-bold block mb-1">পেমেন্টের পরিমাণ (৳) *</label>
            <input name="amount" type="number" min="1" max="${remainingDue}" step="1" required value="${remainingDue}" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm font-bold text-[#105B34]" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">পেমেন্ট মাধ্যম *</label>
            <select name="paymentMethod" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 bg-white text-sm">
              <option value="BKASH">বিকাশ (bKash)</option>
              <option value="NAGAD">নগদ (Nagad)</option>
              <option value="ROCKET">রকেট (Rocket)</option>
              <option value="BANK_TRANSFER">ব্যাংক ট্রান্সফার</option>
              <option value="CASH_ON_PICKUP">সরাসরি নগদ প্রদান</option>
            </select>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-2">
          <div>
            <label class="text-xs font-bold block mb-1">প্রেরক মোবাইল নম্বর *</label>
            <input name="senderNumber" required value="${escapeHtml(order.paymentSenderNumber || "")}" placeholder="017XXXXXXXX" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">Transaction ID *</label>
            <input name="transactionId" required placeholder="TxID লিখুন" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm uppercase" />
          </div>
        </div>
        <div>
          <label class="text-xs font-bold block mb-1">রেফারেন্স নোট (ঐচ্ছিক)</label>
          <input name="referenceNote" placeholder="যেমন: ২য় আংশিক পেমেন্ট" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
        </div>
        <div class="flex gap-2 justify-end pt-1">
          <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
          <button type="submit" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">জমা দিন</button>
        </div>
      </form>
    `);
  },
  async handleOrderPaymentSubmit(e, orderId) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await submitOrderAdvancePayment(orderId, {
      amount: fd.get("amount"),
      paymentMethod: fd.get("paymentMethod"),
      senderNumber: fd.get("senderNumber"),
      transactionId: fd.get("transactionId"),
      referenceNote: fd.get("referenceNote")
    });
  },
  async handleAdminSavePaymentSettings(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await adminSavePaymentSettings({
      bKashNumber: String(fd.get("bKashNumber") || "").trim(),
      bKashAccountType: String(fd.get("bKashAccountType") || "PERSONAL"),
      nagadNumber: String(fd.get("nagadNumber") || "").trim(),
      nagadAccountType: String(fd.get("nagadAccountType") || "PERSONAL"),
      adRemovalFee: Number(fd.get("adRemovalFee") || 200),
      paymentInstructions: String(fd.get("paymentInstructions") || "").trim(),
      notify30Days: fd.get("notify30Days") === "on",
      notify7Days: fd.get("notify7Days") === "on",
      notifyOnExpiry: fd.get("notifyOnExpiry") === "on"
    });
  },
  async handleAdminSendNotification(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const scope = fd.get("targetScope");
    let targetUserId = "ALL";
    let targetRole = "ALL";
    if (scope === "ALL_FARMER") targetRole = "FARMER";
    else if (scope === "ALL_BUYER") targetRole = "BUYER";
    else if (scope === "SPECIFIC") {
      targetUserId = fd.get("specificUserId") || "ALL";
      const u = state.users.find((x) => x.id === targetUserId);
      targetRole = u ? u.role : "ALL";
    }
    const ok = await adminSendNotification({
      targetUserId,
      targetRole,
      titleBn: String(fd.get("titleBn") || ""),
      messageBn: String(fd.get("messageBn") || "")
    });
    if (ok) e.target.reset();
  },
  async handleAdminAddCat(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await adminAddCategory(fd.get("nameBn"), fd.get("nameEn"), fd.get("emoji"));
    e.target.reset();
  },
  async handleAdminAddLoc(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await adminAddLocation(fd.get("divisionBn"), fd.get("districtBn"), fd.get("upazilaBn"), fd.get("unionBn"), "");
    e.target.reset();
  },
  async handleAdminSaveAdConfig(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const mediationAppKey = String(fd.get("mediationAppKey") || "").trim();
    const unityAdsEnabled = fd.get("unityAdsEnabled") === "on";
    const unityGameId = String(fd.get("unityGameId") || "").trim();
    await adminSaveAdConfig({
      adsEnabled: fd.get("adsEnabled") === "on",
      showAdsLabel: fd.get("showAdsLabel") === "on",
      androidAdsEnabled: fd.get("androidAdsEnabled") === "on",
      mediationPlatform: unityAdsEnabled && !mediationAppKey && unityGameId ? "UNITY_DIRECT" : "UNITY_LEVELPLAY",
      mediationAppKey,
      adTestMode: fd.get("adTestMode") === "on",
      bannerEnabled: fd.get("bannerEnabled") === "on",
      androidBannerId: String(fd.get("androidBannerId") || "").trim(),
      nativeEnabled: fd.get("nativeEnabled") === "on",
      androidNativePlacement: String(fd.get("androidNativePlacement") || "").trim(),
      interstitialEnabled: fd.get("interstitialEnabled") === "on",
      androidInterstitialId: String(fd.get("androidInterstitialId") || "").trim(),
      rewardedEnabled: fd.get("rewardedEnabled") === "on",
      androidRewardedId: String(fd.get("androidRewardedId") || "").trim(),
      appOpenEnabled: fd.get("appOpenEnabled") === "on",
      androidAppOpenId: String(fd.get("androidAppOpenId") || "").trim(),
      unityAdsEnabled,
      unityGameId,
      appLovinEnabled: fd.get("appLovinEnabled") === "on",
      appLovinSdkKey: String(fd.get("appLovinSdkKey") || "").trim(),
      metaAudienceEnabled: fd.get("metaAudienceEnabled") === "on",
      metaPlacementId: String(fd.get("metaPlacementId") || "").trim(),
      mintegralEnabled: fd.get("mintegralEnabled") === "on",
      mintegralAppId: String(fd.get("mintegralAppId") || "").trim(),
      pangleEnabled: fd.get("pangleEnabled") === "on",
      pangleAppId: String(fd.get("pangleAppId") || "").trim(),
      inMobiEnabled: fd.get("inMobiEnabled") === "on",
      inMobiAccountId: String(fd.get("inMobiAccountId") || "").trim(),
      liftoffEnabled: fd.get("liftoffEnabled") === "on",
      liftoffAppId: String(fd.get("liftoffAppId") || "").trim(),
      dtExchangeEnabled: fd.get("dtExchangeEnabled") === "on",
      dtExchangeAppId: String(fd.get("dtExchangeAppId") || "").trim(),
      chartboostEnabled: fd.get("chartboostEnabled") === "on",
      chartboostAppId: String(fd.get("chartboostAppId") || "").trim(),
      startIoEnabled: fd.get("startIoEnabled") === "on",
      startIoAppId: String(fd.get("startIoAppId") || "").trim(),
      consentGranted: fd.get("consentGranted") === "on",
      mobileWebAdsEnabled: fd.get("mobileWebAdsEnabled") === "on",
      mobileAdNetworkPriority: String(fd.get("mobileAdNetworkPriority") || "ALL").trim() || "ALL",
      mobileCustomAdHtml: String(fd.get("mobileCustomAdHtml") || ""),
      mobileDirectLinkUrl: String(fd.get("mobileDirectLinkUrl") || "").trim(),
      adsenseEnabled: fd.get("adsenseEnabled") === "on",
      adsenseClient: String(fd.get("adsenseClient") || "").trim(),
      adsenseTopSlot: String(fd.get("adsenseTopSlot") || "").trim(),
      adsenseFeedSlot: String(fd.get("adsenseFeedSlot") || "").trim(),
      adsenseFooterSlot: String(fd.get("adsenseFooterSlot") || "").trim(),
      adsterraEnabled: fd.get("adsterraEnabled") === "on",
      adsterraBannerKey: String(fd.get("adsterraBannerKey") || "").trim(),
      adsterraScriptUrl: String(fd.get("adsterraScriptUrl") || "").trim(),
      adsterraSocialBarUrl: String(fd.get("adsterraSocialBarUrl") || "").trim(),
      monetagEnabled: fd.get("monetagEnabled") === "on",
      monetagZoneId: String(fd.get("monetagZoneId") || "").trim(),
      monetagScriptUrl: String(fd.get("monetagScriptUrl") || "").trim(),
      customAdEnabled: fd.get("customAdEnabled") === "on",
      customAdName: String(fd.get("customAdName") || "").trim() || "Custom Ad",
      customTopHtml: String(fd.get("customTopHtml") || ""),
      customFeedHtml: String(fd.get("customFeedHtml") || ""),
      customFooterHtml: String(fd.get("customFooterHtml") || ""),
      sponsorEnabled: fd.get("sponsorEnabled") === "on",
      sponsorTitle: String(fd.get("sponsorTitle") || "").trim(),
      sponsorSubtitle: String(fd.get("sponsorSubtitle") || "").trim(),
      sponsorImageUrl: String(fd.get("sponsorImageUrl") || "").trim(),
      sponsorTargetUrl: String(fd.get("sponsorTargetUrl") || "").trim(),
      sponsorCtaText: String(fd.get("sponsorCtaText") || "").trim() || "বিস্তারিত দেখুন"
    });
  },
  openReviewModal(orderId) {
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-3">★ কৃষকের ফসল মূল্যায়ন করুন</h3>
      <form onsubmit="window.kbApp.handleReviewSubmit(event, '${escapeHtml(orderId)}')" class="space-y-3">
        <div>
          <label class="text-xs font-bold block mb-1">রেটিং (১ - ৫)</label>
          <select name="rating" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 bg-white text-sm">
            <option value="5">★★★★★ (৫ - অসাধারণ)</option>
            <option value="4">★★★★☆ (৪ - ভালো)</option>
            <option value="3">★★★☆☆ (৩ - মোটামুটি)</option>
            <option value="2">★★☆☆☆ (২ - সন্তোষজনক নয়)</option>
            <option value="1">★☆☆☆☆ (১ - খারাপ)</option>
          </select>
        </div>
        <div>
          <label class="text-xs font-bold block mb-1">আপনার মতামত</label>
          <textarea name="commentBn" rows="3" required placeholder="ফসলের মান কেমন ছিল লিখুন..." class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm"></textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
          <button type="submit" class="px-4 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">জমা দিন</button>
        </div>
      </form>
    `);
  },
  async handleReviewSubmit(e, orderId) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await submitReview(orderId, fd.get("rating"), fd.get("commentBn"));
  },
  openComplaintModal(orderId) {
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-3">অভিযোগ দাখিল করুন</h3>
      <form onsubmit="window.kbApp.handleComplaintSubmit(event, '${escapeHtml(orderId)}')" class="space-y-3">
        <div>
          <label class="text-xs font-bold block mb-1">বিষয় *</label>
          <input name="subjectBn" required placeholder="অভিযোগের সংক্ষিপ্ত বিষয়" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
        </div>
        <div>
          <label class="text-xs font-bold block mb-1">বিস্তারিত বিবরণ</label>
          <textarea name="descriptionBn" rows="3" placeholder="বিস্তারিত লিখুন..." class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm"></textarea>
        </div>
        <div class="flex gap-2 justify-end">
          <button type="button" onclick="window.kbApp.closeModal()" class="px-4 py-2 rounded-xl border text-xs font-bold">বাতিল</button>
          <button type="submit" class="px-4 py-2 rounded-xl bg-red-600 text-white text-xs font-bold">জমা দিন</button>
        </div>
      </form>
    `);
  },
  async handleComplaintSubmit(e, orderId) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await submitComplaint(orderId, fd.get("subjectBn"), fd.get("descriptionBn"));
  },
  closeModal,
  async requestBrowserNotifications() {
    if (typeof Notification === "undefined") return;
    const perm = await Notification.requestPermission();
    showToast(perm === "granted" ? "ব্রাউজার নোটিফিকেশন চালু হয়েছে!" : "অনুমতি দেওয়া হয়নি।", perm === "granted" ? "success" : "info");
    renderCurrentView();
  }
};

// Close modal on backdrop click
document.getElementById("modal-root")?.addEventListener("click", (e) => {
  if (e.target.id === "modal-root") closeModal();
});

// Live Online/Offline Network Listeners
if (typeof window !== "undefined") {
  window.addEventListener("online", () => {
    state.isOnline = true;
    updateHeaderChrome();
  });
  window.addEventListener("offline", () => {
    state.isOnline = false;
    updateHeaderChrome();
  });
}

// Boot
loadLocalSnapshot();
renderCurrentView();
initFirebaseRealtime();
