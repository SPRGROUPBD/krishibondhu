import {
  RTDB_CONFIG,
  SUPERADMIN_CREDENTIALS,
  DEFAULT_AD_CONFIG,
  DEFAULT_PAYMENT_SETTINGS,
  MON_TO_KG,
  FIXED_ACTIVATION_FEE_BDT,
  FIXED_VALIDITY_YEARS,
  ACCOUNT_STATUS_META,
  ACTIVATION_PAYMENT_STATUS_META,
  MASTER_CATEGORIES,
  BD_DIVISIONS,
  BD_DISTRICTS,
  ORDER_STATUS_META,
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
    wholesaleOnly: "",
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
  infoTab: "about",
  adConfig: { ...DEFAULT_AD_CONFIG }
};

let rtdbRef = null;
let rtdbApi = null;
let lastAdScriptSignature = "";
const alertedNotifIds = new Set();

// ---------------------- Persistence (LocalStorage + RTDB) ----------------------

function loadLocalSnapshot() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const saved = JSON.parse(raw);
    state.lang = saved.lang === "en" ? "en" : "bn";
    state.activeUserId = saved.activeUserId || "";
    if (Array.isArray(saved.users)) state.users = saved.users.filter((u) => !u.isDemoRecord);
    if (Array.isArray(saved.categories) && saved.categories.length) state.categories = mergeById(MASTER_CATEGORIES, saved.categories);
    if (Array.isArray(saved.locations) && saved.locations.length) state.locations = mergeById(buildInitialSeedLocations(), saved.locations);
    if (Array.isArray(saved.products)) state.products = saved.products.filter((p) => !p.isDemoRecord);
    if (Array.isArray(saved.orders)) state.orders = saved.orders.filter((o) => !o.isDemoRecord);
    if (Array.isArray(saved.activationPayments)) state.activationPayments = saved.activationPayments;
    if (saved.paymentSettings && typeof saved.paymentSettings === "object") {
      state.paymentSettings = { ...DEFAULT_PAYMENT_SETTINGS, ...saved.paymentSettings };
    }
    if (Array.isArray(saved.notifications)) state.notifications = saved.notifications;
    if (Array.isArray(saved.reviews)) state.reviews = saved.reviews;
    if (Array.isArray(saved.complaints)) state.complaints = saved.complaints;
    if (Array.isArray(saved.auditLogs)) state.auditLogs = saved.auditLogs;
    if (Array.isArray(saved.cart)) state.cart = saved.cart;
    if (saved.adConfig && typeof saved.adConfig === "object") {
      state.adConfig = { ...DEFAULT_AD_CONFIG, ...saved.adConfig };
    }
  } catch (err) {
    console.warn("Local cache restore skipped:", err);
  }
}

function saveLocalSnapshot() {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        lang: state.lang,
        activeUserId: state.activeUserId,
        users: state.users,
        categories: state.categories,
        locations: state.locations,
        products: state.products,
        orders: state.orders,
        activationPayments: state.activationPayments,
        paymentSettings: state.paymentSettings,
        notifications: state.notifications,
        reviews: state.reviews,
        complaints: state.complaints,
        auditLogs: state.auditLogs,
        cart: state.cart,
        adConfig: state.adConfig
      })
    );
  } catch (err) {
    console.warn("Local cache save skipped:", err);
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

    onValue(child(rtdbRef, "users"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        const list = Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
        // Keep local admin_super if active offline
        const localAdmin = state.users.find((u) => u.id === SUPERADMIN_CREDENTIALS.userId);
        state.users = localAdmin && !list.some((u) => u.id === SUPERADMIN_CREDENTIALS.userId)
          ? [localAdmin, ...list]
          : list;
        evaluateAccountExpiries(Date.now(), false);
        saveLocalSnapshot();
        renderCurrentView();
      }
    });

    onValue(child(rtdbRef, "categories"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        const remoteCats = Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
        state.categories = mergeById(MASTER_CATEGORIES, remoteCats).sort((a, b) => (a.sortOrder || 99) - (b.sortOrder || 99));
        saveLocalSnapshot();
        renderCurrentView();
      }
    });

    onValue(child(rtdbRef, "locations"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        const remoteLocs = Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
        state.locations = mergeById(buildInitialSeedLocations(), remoteLocs);
        saveLocalSnapshot();
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
      saveLocalSnapshot();
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
      saveLocalSnapshot();
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
      saveLocalSnapshot();
      updateHeaderChrome();
      if (state.page === "notifications" || state.page === "farmer-dashboard") {
        renderCurrentView();
      }
    });

    onChildAdded(child(rtdbRef, "notifications"), (snap) => {
      const n = snap.val();
      if (!n) return;
      const item = { id: snap.key, ...n };
      upsertArrayItem(state.notifications, item);
      state.notifications.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
      checkAndAlertIncomingNotifications();
      updateHeaderChrome();
    });

    onValue(child(rtdbRef, "reviews"), (snap) => {
      const val = snap.val();
      state.reviews = val && typeof val === "object"
        ? Object.entries(val).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      saveLocalSnapshot();
    });

    onValue(child(rtdbRef, "complaints"), (snap) => {
      const val = snap.val();
      state.complaints = val && typeof val === "object"
        ? Object.entries(val).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      saveLocalSnapshot();
    });

    onValue(child(rtdbRef, "auditLogs"), (snap) => {
      const val = snap.val();
      state.auditLogs = val && typeof val === "object"
        ? Object.entries(val).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        : [];
      saveLocalSnapshot();
    });

    onValue(child(rtdbRef, "adConfig"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        state.adConfig = { ...DEFAULT_AD_CONFIG, ...val };
        saveLocalSnapshot();
        renderCurrentView();
      }
    });

    onValue(child(rtdbRef, "activationPayments"), (snap) => {
      const val = snap.val();
      state.activationPayments = val && typeof val === "object"
        ? Object.entries(val).map(([k, v]) => ({ id: k, ...v })).sort((a, b) => (b.submittedAt || 0) - (a.submittedAt || 0))
        : [];
      saveLocalSnapshot();
      renderCurrentView();
    });

    onValue(child(rtdbRef, "paymentSettings"), (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        state.paymentSettings = { ...DEFAULT_PAYMENT_SETTINGS, ...val };
        saveLocalSnapshot();
        renderCurrentView();
      }
    });
  } catch (err) {
    console.warn("Firebase RTDB running in local fallback mode:", err);
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

async function rtdbFetchUserByPhone(phone) {
  if (!rtdbRef || !rtdbApi) return null;
  const target = normalizePhone(phone);
  try {
    const q = rtdbApi.query(
      rtdbApi.child(rtdbRef, "users"),
      rtdbApi.orderByChild("phone"),
      rtdbApi.equalTo(target)
    );
    const snap = await rtdbApi.get(q);
    if (!snap.exists()) return null;
    const val = snap.val();
    const [key, data] = Object.entries(val)[0];
    return { id: key, ...data };
  } catch (err) {
    console.warn("RTDB phone lookup skipped:", err);
    return null;
  }
}

async function rtdbFetchAllActivationPayments() {
  if (!rtdbRef || !rtdbApi) return state.activationPayments;
  try {
    const snap = await rtdbApi.get(rtdbApi.child(rtdbRef, "activationPayments"));
    if (!snap.exists()) return state.activationPayments;
    const val = snap.val();
    if (!val || typeof val !== "object") return state.activationPayments;
    return Object.entries(val).map(([k, v]) => ({ id: k, ...v }));
  } catch (err) {
    console.warn("RTDB activationPayments lookup skipped:", err);
    return state.activationPayments;
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
  return state.users.find((u) => u.id === state.activeUserId) || null;
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
    ? state.orders.filter((o) => o.farmerId === user.id && o.status === "PENDING").length
    : 0;

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
  let user = state.users.find((u) => normalizePhone(u.phone) === normalizedPhone || u.phone === cleanId);
  if (!user) {
    const remote = await rtdbFetchUserByPhone(normalizedPhone);
    if (remote) {
      upsertArrayItem(state.users, remote);
      user = remote;
    }
  }
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
  if (!isMarketplaceActive(refreshed)) {
    navigate((refreshed.accountStatus || "PAYMENT_PENDING") === "SUSPENDED" ? "profile" : "activation");
  } else {
    navigate("home");
  }
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
  if (state.users.some((u) => normalizePhone(u.phone) === phone) || (await rtdbFetchUserByPhone(phone))) {
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

async function submitActivationPayment({ paymentMethod, senderMobile, transactionId, amount, receiptUri, isRenewal = false }) {
  const user = getActiveUser();
  if (!user || user.role === "ADMIN") {
    showToast("অ্যাক্টিভেশন ফি জমা দিতে কৃষক বা ক্রেতা হিসেবে লগইন করুন।", "error");
    return false;
  }
  if (user.accountStatus === "SUSPENDED") {
    showToast("আপনার অ্যাকাউন্ট স্থগিত (Suspended) রয়েছে।", "error");
    return false;
  }
  const hasPending = state.activationPayments.some((p) => p.userId === user.id && p.status === "PENDING");
  if (hasPending) {
    showToast("আপনার একটি পেমেন্ট ইতোমধ্যে অ্যাডমিন যাচাইয়ের জন্য অপেক্ষমাণ রয়েছে।", "error");
    return false;
  }

  const cleanSender = normalizePhone(senderMobile);
  const cleanTxId = normalizeTxId(transactionId);
  const cleanReceipt = String(receiptUri || "").trim();
  const feeAmount = Number(amount || FIXED_ACTIVATION_FEE_BDT);

  if (cleanSender.length < 10) {
    showToast("সঠিক প্রেরক মোবাইল নম্বর (যেমন: 017XXXXXXXX) দিন।", "error");
    return false;
  }
  if (cleanTxId.length < 4) {
    showToast("সঠিক ট্রানজেকশন আইডি (Transaction ID) দিন।", "error");
    return false;
  }
  if (feeAmount !== FIXED_ACTIVATION_FEE_BDT) {
    showToast(`অ্যাক্টিভেশন ও নবায়ন ফি নির্ধারিত ৳${FIXED_ACTIVATION_FEE_BDT}।`, "error");
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
  const payment = {
    id: shortId("actpay"),
    userId: user.id,
    userName: user.fullName,
    userPhone: user.phone,
    userRole: user.role,
    accountType: user.role,
    paymentMethod: paymentMethod === "NAGAD" ? "NAGAD" : "BKASH",
    senderMobile: cleanSender,
    transactionId: cleanTxId,
    amount: FIXED_ACTIVATION_FEE_BDT,
    validityYears: FIXED_VALIDITY_YEARS,
    receiptUri: cleanReceipt,
    status: "PENDING",
    isRenewal: Boolean(isRenewal || user.activationDate > 0),
    adminNotes: "",
    submittedAt: now,
    reviewedAt: 0,
    reviewedBy: ""
  };

  state.activationPayments.unshift(payment);
  if (user.accountStatus !== "ACTIVE") {
    user.accountStatus = "PAYMENT_UNDER_REVIEW";
  }
  user.lastPaymentId = payment.id;
  user.updatedAt = now;

  const adminNotif = {
    id: shortId("not"),
    userId: "ALL",
    targetRole: "ADMIN",
    senderRole: user.role,
    orderId: payment.id,
    type: "ACTIVATION_PAYMENT_SUBMITTED",
    titleBn: `নতুন অ্যাকাউন্ট অ্যাক্টিভেশন পেমেন্ট (৳${FIXED_ACTIVATION_FEE_BDT})`,
    messageBn: `${user.fullName} (${user.role === "FARMER" ? "কৃষক" : "ক্রেতা"}) • ${payment.paymentMethod} TxID: ${cleanTxId}`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: user.id,
    actorRole: user.role,
    actionBn: payment.isRenewal ? "অ্যাকাউন্ট নবায়ন পেমেন্ট জমা (ওয়েব)" : "অ্যাকাউন্ট অ্যাক্টিভেশন পেমেন্ট জমা (ওয়েব)",
    targetEntity: "ActivationPayment",
    targetId: payment.id,
    detailsBn: `${user.fullName} — ৳${FIXED_ACTIVATION_FEE_BDT} | TxID: ${cleanTxId}`,
    createdAt: now
  };
  state.notifications.unshift(adminNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`activationPayments/${payment.id}`, payment);
  await rtdbPush(`users/${user.id}`, user);
  await rtdbPush(`notifications/${adminNotif.id}`, adminNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast("পেমেন্ট তথ্য জমা হয়েছে! অ্যাডমিন যাচাইয়ের পর ২ বছরের জন্য অ্যাকাউন্ট সক্রিয় হবে।", "success");
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
  const baseDate = user.accountStatus === "ACTIVE" && Number(user.expiryDate || 0) > now ? Number(user.expiryDate) : now;
  const nextExpiry = addCalendarYears(baseDate, FIXED_VALIDITY_YEARS);

  payment.status = "APPROVED";
  payment.reviewedAt = now;
  payment.reviewedBy = admin.id;
  payment.adminNotes = String(notes || "").trim();

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
  const cleanReason = String(reason || "").trim() || (requestResubmission ? "সঠিক TxID ও স্পষ্ট রসিদ পুনরায় জমা দিন।" : "পেমেন্ট তথ্য যাচাইয়ে অমিল পাওয়া গেছে।");

  payment.status = requestResubmission ? "RESUBMISSION_REQUESTED" : "REJECTED";
  payment.reviewedAt = now;
  payment.reviewedBy = admin.id;
  payment.adminNotes = cleanReason;

  if (user) {
    const stillValidActive = user.accountStatus === "ACTIVE" && Number(user.expiryDate || 0) > now;
    if (!stillValidActive) {
      user.accountStatus = requestResubmission ? "PAYMENT_PENDING" : "PAYMENT_REJECTED";
    }
    user.updatedAt = now;
    await rtdbPush(`users/${user.id}`, user);

    const notif = {
      id: shortId("not"),
      userId: user.id,
      targetRole: user.role,
      senderRole: "ADMIN",
      orderId: payment.id,
      type: "ACTIVATION_REJECTED",
      titleBn: requestResubmission ? "পেমেন্ট পুনঃজমা প্রয়োজন" : "অ্যাক্টিভেশন পেমেন্ট প্রত্যাখ্যাত",
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

async function adminSavePaymentSettings(nextSettings) {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র সুপার অ্যাডমিন পেমেন্ট সেটিংস পরিবর্তন করতে পারেন।", "error");
    return false;
  }
  const now = Date.now();
  const merged = {
    ...DEFAULT_PAYMENT_SETTINGS,
    ...nextSettings,
    farmerFee: FIXED_ACTIVATION_FEE_BDT,
    buyerFee: FIXED_ACTIVATION_FEE_BDT,
    renewalFee: FIXED_ACTIVATION_FEE_BDT,
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
    detailsBn: `bKash: ${merged.bKashNumber} (${merged.bKashAccountType}) | Nagad: ${merged.nagadNumber} (${merged.nagadAccountType})`,
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

async function placeOrdersFromCart({ deliveryOption, deliveryAddress, paymentMethod, transactionRef }) {
  const buyer = requireActiveMarketplaceUser("অর্ডার নিশ্চিত");
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

  const now = Date.now();
  const grouped = new Map();
  for (const item of state.cart) {
    if (!grouped.has(item.farmerId)) grouped.set(item.farmerId, []);
    grouped.get(item.farmerId).push(item);
  }

  const createdOrders = [];
  for (const [farmerId, farmerItems] of grouped.entries()) {
    const farmer = state.users.find((u) => u.id === farmerId);
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
    const itemsSummary = farmerItems.map((i) => `${i.productNameBn} × ${formatQty(i.quantity)} মণ (${formatQty(i.quantity * MON_TO_KG)} কেজি)`).join(", ");
    const cleanTx = normalizeTxId(transactionRef || "");

    const order = {
      id: orderId,
      orderCode,
      buyerId: buyer.id,
      buyerName: buyer.fullName,
      buyerPhone: buyer.phone,
      farmerId,
      farmerName: farmer?.fullName || farmerItems[0].farmerName,
      farmerPhone: farmer?.phone || "",
      status: "PENDING",
      deliveryOption,
      deliveryAddress: deliveryOption === "FARM_PICKUP" ? "সরাসরি কৃষকের খামার থেকে সংগ্রহ" : deliveryAddress.trim(),
      division: farmer?.division || buyer.division,
      district: farmer?.district || buyer.district,
      upazila: farmer?.upazila || buyer.upazila,
      productTotal,
      deliveryCharge,
      grandTotal,
      paymentMethod: paymentMethod || "CASH_ON_PICKUP",
      paymentSenderNumber: "",
      transactionReference: cleanTx,
      paymentReceiptUri: "",
      paymentStatus: cleanTx ? "SUBMITTED" : "PENDING",
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

    const farmerNotif = {
      id: shortId("not"),
      userId: farmerId,
      targetRole: "FARMER",
      senderRole: "BUYER",
      orderId,
      type: "ORDER_NEW",
      titleBn: `নতুন অর্ডার এসেছে (${orderCode})`,
      messageBn: `ক্রেতা ${buyer.fullName} (${buyer.upazila}, ${buyer.district}) ${itemsSummary} অর্ডার করেছেন — মোট ${formatBdt(grandTotal)}।`,
      isRead: false,
      createdAt: now
    };

    state.orders.unshift(order);
    state.notifications.unshift(farmerNotif);
    createdOrders.push(order);

    await rtdbPush(`orders/${orderId}`, order);
    await rtdbPush(`notifications/${farmerNotif.id}`, farmerNotif);
  }

  state.cart = [];
  saveLocalSnapshot();
  showToast(`${createdOrders.length}টি অর্ডার জমা হয়েছে! কৃষক অর্ডার গ্রহণ করলে পেমেন্ট জমা দিন।`, "success");
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
    showToast("ফসলের নাম, সঠিক মণ পরিমাণ ও প্রতি মণের দাম দিন।", "error");
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
    isWholesale: Boolean(form.isWholesale),
    isPopular: false,
    status: "ACTIVE",
    createdAt: now,
    updatedAt: now
  };

  state.products.unshift(product);
  const audit = {
    id: shortId("aud"),
    actorId: farmer.id,
    actorRole: farmer.role,
    actionBn: "নতুন ফসল যুক্ত (ওয়েব)",
    targetEntity: "Product",
    targetId: product.id,
    detailsBn: `${product.nameBn} (${product.district}) — ${formatBdt(product.pricePerUnit)}/মণ`,
    createdAt: now
  };
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`products/${product.id}`, product);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast("আপনার ফসল সফলভাবে যুক্ত ও লাইভ করা হয়েছে!", "success");
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
  prod.status = s > 0 ? "ACTIVE" : "OUT_OF_STOCK";
  prod.updatedAt = Date.now();
  saveLocalSnapshot();
  await rtdbPush(`products/${prod.id}`, prod);
  showToast("দাম ও মজুত আপডেট হয়েছে!", "success");
  renderCurrentView();
}

async function submitOrderPayment(orderId, { paymentMethod, senderNumber, transactionReference, receiptUri }) {
  const buyer = requireActiveMarketplaceUser("অর্ডার পেমেন্ট জমা");
  if (!buyer) return false;
  const order = state.orders.find((o) => o.id === orderId);
  if (!order || order.buyerId !== buyer.id) {
    showToast("শুধুমাত্র অর্ডারকারী ক্রেতা পেমেন্ট তথ্য জমা দিতে পারবেন।", "error");
    return false;
  }
  if (["REJECTED", "CANCELLED"].includes(order.status)) {
    showToast("বাতিল বা প্রত্যাখ্যাত অর্ডারে পেমেন্ট জমা দেওয়া যাবে না।", "error");
    return false;
  }
  if (order.status === "PENDING") {
    showToast("কৃষক অর্ডারটি গ্রহণ (Accept) করার পর পেমেন্ট জমা দিন।", "error");
    return false;
  }
  const cleanSender = normalizePhone(senderNumber) || buyer.phone;
  const cleanTx = normalizeTxId(transactionReference);
  const cleanReceipt = String(receiptUri || "").trim();
  const isDigital = !["CASH_ON_PICKUP", "CASH_ON_DELIVERY"].includes(paymentMethod);
  if (isDigital && cleanTx.length < 4 && !cleanReceipt) {
    showToast("ডিজিটাল পেমেন্টের জন্য Transaction ID অথবা রসিদ ছবি দিন।", "error");
    return false;
  }

  const now = Date.now();
  order.paymentMethod = paymentMethod || "BKASH";
  order.paymentSenderNumber = cleanSender;
  order.transactionReference = cleanTx;
  order.paymentReceiptUri = cleanReceipt;
  order.paymentStatus = "SUBMITTED";
  order.updatedAt = now;

  const farmerNotif = {
    id: shortId("not"),
    userId: order.farmerId,
    targetRole: "FARMER",
    senderRole: "BUYER",
    orderId: order.id,
    type: "ORDER_PAYMENT_SUBMITTED",
    titleBn: `ক্রেতা পেমেন্ট তথ্য জমা দিয়েছেন (${order.orderCode})`,
    messageBn: `ক্রেতা ${buyer.fullName} ${formatBdt(order.grandTotal)} (TxID: ${cleanTx || "রসিদ"}) পাঠিয়েছেন। টাকা বুঝে পেলে 'টাকা বুঝে পেয়েছি' নিশ্চিত করুন।`,
    isRead: false,
    createdAt: now
  };
  const adminNotif = {
    id: shortId("not"),
    userId: "ALL",
    targetRole: "ADMIN",
    senderRole: "BUYER",
    orderId: order.id,
    type: "ORDER_PAYMENT_VERIFY_REQ",
    titleBn: `অর্ডার পেমেন্ট যাচাই প্রয়োজন (${order.orderCode})`,
    messageBn: `${buyer.fullName} -> ${order.farmerName}: ${formatBdt(order.grandTotal)} (TxID: ${cleanTx || "রসিদ সংযুক্ত"})।`,
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: buyer.id,
    actorRole: buyer.role,
    actionBn: "অর্ডার পেমেন্ট তথ্য জমা (ওয়েব)",
    targetEntity: "Order",
    targetId: order.id,
    detailsBn: `${order.orderCode} — ${order.paymentMethod} | TxID: ${cleanTx || "রসিদ"}`,
    createdAt: now
  };
  state.notifications.unshift(farmerNotif, adminNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`orders/${order.id}`, order);
  await rtdbPush(`notifications/${farmerNotif.id}`, farmerNotif);
  await rtdbPush(`notifications/${adminNotif.id}`, adminNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast("পেমেন্ট তথ্য জমা হয়েছে! কৃষকের টাকা প্রাপ্তি ও অ্যাডমিন যাচাইয়ের পর অর্ডার নিশ্চিত হবে।", "success");
  closeModal();
  renderCurrentView();
  return true;
}

async function farmerConfirmPaymentReceived(orderId) {
  const farmer = requireActiveMarketplaceUser("টাকা প্রাপ্তি নিশ্চিতকরণ");
  if (!farmer) return false;
  const order = state.orders.find((o) => o.id === orderId);
  if (!order) return false;
  if (order.farmerId !== farmer.id && farmer.role !== "ADMIN") {
    showToast("শুধুমাত্র সংশ্লিষ্ট কৃষক টাকা বুঝে পাওয়ার নিশ্চিতকরণ দিতে পারেন।", "error");
    return false;
  }
  if (["PENDING", "REJECTED", "CANCELLED"].includes(order.status)) {
    showToast("এই অবস্থায় টাকা প্রাপ্তি নিশ্চিত করা যাবে না।", "error");
    return false;
  }

  const now = Date.now();
  order.farmerPaymentConfirmed = true;
  order.farmerPaymentConfirmedAt = now;
  const readyToConfirm = ["ACCEPTED", "PROCESSING", "READY_FOR_PICKUP", "READY", "SHIPPED", "DELIVERED"].includes(order.status);
  if (order.adminPaymentVerified && readyToConfirm) {
    order.status = "CONFIRMED";
    order.paymentStatus = "PAID";
  }
  order.updatedAt = now;

  const buyerNotif = {
    id: shortId("not"),
    userId: order.buyerId,
    targetRole: "BUYER",
    senderRole: "FARMER",
    orderId: order.id,
    type: "FARMER_PAYMENT_CONFIRMED",
    titleBn: `কৃষক টাকা বুঝে পেয়েছেন (${order.orderCode})`,
    messageBn: order.adminPaymentVerified
      ? "কৃষক টাকা প্রাপ্তি নিশ্চিত করেছেন এবং অ্যাডমিন যাচাই সম্পন্ন হওয়ায় অর্ডারটি চূড়ান্ত নিশ্চিত (Confirmed) হয়েছে।"
      : "কৃষক টাকা পাওয়ার বিষয়টি নিশ্চিত করেছেন। অ্যাডমিন যাচাইয়ের পর চূড়ান্ত কনফার্মেশন সম্পন্ন হবে।",
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: farmer.id,
    actorRole: farmer.role,
    actionBn: "কৃষক কর্তৃক টাকা প্রাপ্তি নিশ্চিতকরণ (ওয়েব)",
    targetEntity: "Order",
    targetId: order.id,
    detailsBn: `${order.orderCode} — কৃষক ${farmer.fullName} টাকা বুঝে পেয়েছেন`,
    createdAt: now
  };
  state.notifications.unshift(buyerNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`orders/${order.id}`, order);
  await rtdbPush(`notifications/${buyerNotif.id}`, buyerNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast(order.status === "CONFIRMED" ? "অর্ডার চূড়ান্তভাবে Confirmed হয়েছে!" : "টাকা প্রাপ্তি নিশ্চিত হয়েছে (অ্যাডমিন যাচাই অপেক্ষমাণ)।", "success");
  renderCurrentView();
  return true;
}

async function adminVerifyOrderPayment(orderId, approved, notes = "") {
  const admin = getActiveUser();
  if (!admin || admin.role !== "ADMIN") {
    showToast("শুধুমাত্র সুপার অ্যাডমিন অর্ডার পেমেন্ট যাচাই করতে পারেন।", "error");
    return false;
  }
  const order = state.orders.find((o) => o.id === orderId);
  if (!order) return false;

  const now = Date.now();
  order.adminPaymentVerified = Boolean(approved);
  order.adminPaymentVerifiedBy = admin.id;
  order.adminPaymentVerifiedAt = now;
  order.paymentStatus = approved ? "VERIFIED" : "REJECTED";
  if (!approved) {
    order.farmerPaymentConfirmed = false;
  } else {
    const readyToConfirm = ["ACCEPTED", "PROCESSING", "READY_FOR_PICKUP", "READY", "SHIPPED", "DELIVERED"].includes(order.status);
    if (order.farmerPaymentConfirmed && readyToConfirm) {
      order.status = "CONFIRMED";
      order.paymentStatus = "PAID";
    }
  }
  order.updatedAt = now;

  const buyerNotif = {
    id: shortId("not"),
    userId: order.buyerId,
    targetRole: "BUYER",
    senderRole: "ADMIN",
    orderId: order.id,
    type: "ORDER_PAYMENT_VERIFICATION",
    titleBn: approved ? `অর্ডার পেমেন্ট যাচাই সম্পন্ন (${order.orderCode})` : `অর্ডার পেমেন্ট যাচাই প্রত্যাখ্যাত (${order.orderCode})`,
    messageBn: approved && order.farmerPaymentConfirmed
      ? "অ্যাডমিন যাচাই এবং কৃষকের টাকা প্রাপ্তি নিশ্চিতকরণ সম্পন্ন হওয়ায় অর্ডারটি চূড়ান্ত Confirmed হয়েছে।"
      : approved
      ? "অ্যাডমিন পেমেন্ট যাচাই করেছেন। কৃষক 'টাকা বুঝে পেয়েছি' নিশ্চিত করলে অর্ডার চূড়ান্ত Confirmed হবে।"
      : (notes || "পেমেন্ট তথ্যে অমিল পাওয়া গেছে। অনুগ্রহ করে সঠিক তথ্য জমা দিন।"),
    isRead: false,
    createdAt: now
  };
  const audit = {
    id: shortId("aud"),
    actorId: admin.id,
    actorRole: "ADMIN",
    actionBn: `অর্ডার পেমেন্ট যাচাই (${order.paymentStatus})`,
    targetEntity: "Order",
    targetId: order.id,
    detailsBn: `${order.orderCode} — ${order.paymentStatus}${notes ? ` (${notes})` : ""}`,
    createdAt: now
  };
  state.notifications.unshift(buyerNotif);
  state.auditLogs.unshift(audit);
  saveLocalSnapshot();

  await rtdbPush(`orders/${order.id}`, order);
  await rtdbPush(`notifications/${buyerNotif.id}`, buyerNotif);
  await rtdbPush(`auditLogs/${audit.id}`, audit);
  showToast(approved ? "অর্ডার পেমেন্ট যাচাই অনুমোদিত!" : "অর্ডার পেমেন্ট প্রত্যাখ্যাত হয়েছে।", approved ? "success" : "info");
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
  if (isBuyer && newStatus === "CANCELLED" && order.status !== "PENDING") {
    showToast("অর্ডার প্রক্রিয়াধীন হলে বাতিল করা যাবে না।", "error");
    return;
  }

  // Strict Security Rule: Order CANNOT be Confirmed or Completed using buyer screenshot alone!
  if (newStatus === "CONFIRMED" || newStatus === "COMPLETED") {
    if (!order.farmerPaymentConfirmed) {
      showToast("কৃষক টাকা বুঝে পাওয়ার নিশ্চিতকরণ ('টাকা বুঝে পেয়েছি') দেওয়ার আগে অর্ডার কনফার্ম বা সম্পন্ন হবে না।", "error");
      return;
    }
    if (!order.adminPaymentVerified) {
      showToast("শুধু ক্রেতার পেমেন্ট রসিদ বা স্ক্রিনশট দিয়ে অর্ডার কনফার্ম হবে না — অ্যাডমিন পেমেন্ট যাচাই সম্পন্ন হতে হবে।", "error");
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
    detailsBn: `Ads: ${merged.adsEnabled ? "চালু" : "বন্ধ"}, AdSense: ${merged.adsenseEnabled ? "চালু" : "বন্ধ"}, Adsterra: ${merged.adsterraEnabled ? "চালু" : "বন্ধ"}, Monetag: ${merged.monetagEnabled ? "চালু" : "বন্ধ"}, AdMob: ${merged.androidAdsEnabled ? "চালু" : "বন্ধ"}`,
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

function syncExternalAdNetworkScripts() {
  const cfg = state.adConfig || DEFAULT_AD_CONFIG;
  const sig = JSON.stringify({
    e: cfg.adsEnabled,
    ac: cfg.adsenseEnabled ? cfg.adsenseClient : "",
    asb: cfg.adsterraEnabled ? cfg.adsterraSocialBarUrl : "",
    m: cfg.monetagEnabled ? `${cfg.monetagZoneId}|${cfg.monetagScriptUrl}` : ""
  });
  if (sig === lastAdScriptSignature) return;
  lastAdScriptSignature = sig;

  const scriptsRoot = document.getElementById("ad-scripts-root");
  if (!scriptsRoot) return;
  scriptsRoot.innerHTML = "";
  if (!cfg.adsEnabled) return;

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
  if (!cfg.adsEnabled) return "";

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
  const topEl = document.getElementById("ad-slot-top");
  const footerEl = document.getElementById("ad-slot-footer");

  if (topEl) topEl.innerHTML = cfg.adsEnabled ? renderAdPlacement("top") : "";
  if (footerEl) footerEl.innerHTML = cfg.adsEnabled ? renderAdPlacement("footer") : "";

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

function getUnionOptions(district, upazila) {
  const set = new Set();
  for (const loc of state.locations || []) {
    if (district && loc.districtBn !== district) continue;
    if (upazila && loc.upazilaBn !== upazila) continue;
    if (loc.unionBn && String(loc.unionBn).trim()) set.add(String(loc.unionBn).trim());
  }
  for (const u of state.users || []) {
    if (district && u.district !== district) continue;
    if (upazila && u.upazila !== upazila) continue;
    if (u.unionName && String(u.unionName).trim()) set.add(String(u.unionName).trim());
  }
  for (const p of state.products || []) {
    if (district && p.district !== district) continue;
    if (upazila && p.upazila !== upazila) continue;
    if (p.unionName && String(p.unionName).trim()) set.add(String(p.unionName).trim());
  }
  return Array.from(set).sort();
}

function getFilteredProducts() {
  const f = state.filters;
  const q = f.query.trim().toLowerCase();
  const list = state.products.filter((p) => {
    if (p.status !== "ACTIVE") return false;
    const matchQ =
      !q ||
      (p.nameBn || "").toLowerCase().includes(q) ||
      (p.nameEn || "").toLowerCase().includes(q) ||
      (p.farmerName || "").toLowerCase().includes(q) ||
      (p.division || "").toLowerCase().includes(q) ||
      (p.district || "").toLowerCase().includes(q) ||
      (p.upazila || "").toLowerCase().includes(q) ||
      (p.unionName || "").toLowerCase().includes(q) ||
      (p.categoryNameBn || "").toLowerCase().includes(q);
    const matchCat = !f.categoryId || p.categoryId === f.categoryId;
    const matchDiv = !f.division || p.division === f.division;
    const matchDist = !f.district || p.district === f.district;
    const matchUpz = !f.upazila || p.upazila === f.upazila;
    const matchUnion = !f.union || (p.unionName || "").trim() === f.union;
    const matchW = f.wholesaleOnly === "" || String(Boolean(p.isWholesale)) === f.wholesaleOnly;
    return matchQ && matchCat && matchDiv && matchDist && matchUpz && matchUnion && matchW;
  });

  if (f.sort === "PRICE_LOW_HIGH") return [...list].sort((a, b) => a.pricePerUnit - b.pricePerUnit);
  if (f.sort === "PRICE_HIGH_LOW") return [...list].sort((a, b) => b.pricePerUnit - a.pricePerUnit);
  return [...list].sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
}

function getFilteredFarmers() {
  const f = state.filters;
  const q = f.query.trim().toLowerCase();
  return state.users.filter((u) => {
    if (u.role !== "FARMER") return false;
    const matchQ =
      !q ||
      (u.fullName || "").toLowerCase().includes(q) ||
      (u.division || "").toLowerCase().includes(q) ||
      (u.district || "").toLowerCase().includes(q) ||
      (u.upazila || "").toLowerCase().includes(q) ||
      (u.unionName || "").toLowerCase().includes(q) ||
      (u.farmBio || "").toLowerCase().includes(q);
    const matchDiv = !f.division || u.division === f.division;
    const matchDist = !f.district || u.district === f.district;
    const matchUpz = !f.upazila || u.upazila === f.upazila;
    const matchUnion = !f.union || (u.unionName || "").trim() === f.union;
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
        ${p.isWholesale ? `<span class="absolute top-3 left-3 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500 text-white shadow">পাইকারি</span>` : ""}
        ${p.farmerVerified ? `<span class="absolute top-3 right-3 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-[#168447] text-white shadow">✓ যাচাইকৃত</span>` : ""}
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
            : `<div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">${myProducts.slice(0, 4).map(renderProductCard).join("")}</div>`
        }
      </section>

      ${renderAdPlacement("feed")}
    </div>
  `;
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
  const topCats = state.categories.slice(0, 12);
  const myBuyerOrders = isBuyer ? state.orders.filter((o) => o.buyerId === user.id) : [];

  return `
    <div class="space-y-8">
      ${
        isAdmin
          ? `<div class="bg-amber-50 border border-amber-200 rounded-3xl p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 class="font-bold text-amber-900">🛡️ সুপার অ্যাডমিন নিয়ন্ত্রণ প্যানেল</h2>
                <p class="text-xs text-amber-800">কৃষক ও ক্রেতা যাচাই, বিজ্ঞাপন নেটওয়ার্ক, অর্ডার ও অডিট লগ পরিচালনা করুন।</p>
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

      <!-- Hero Banner -->
      <section class="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#105B34] via-[#168447] to-emerald-600 text-white shadow-lg">
        <div class="grid md:grid-cols-2 gap-6 items-center p-6 sm:p-10">
          <div class="space-y-4">
            <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/15 backdrop-blur text-xs font-semibold">
              <span>🇧🇩 কুষ্টিয়া থেকে সারা বাংলাদেশ (৬৪ জেলা)</span>
              <span class="px-2 py-0.5 rounded-full bg-amber-400 text-[#105B34] font-bold">০% দালাল</span>
            </div>
            <h1 class="text-2xl sm:text-4xl font-extrabold leading-tight">
              ${
                isBuyer
                  ? "সরাসরি খামার থেকে তাজা কৃষিপণ্য কিনুন ন্যায্য দামে।"
                  : "কৃষকের ফসল, কৃষকের ন্যায্য দাম — সরাসরি ক্রেতার হাতে।"
              }
            </h1>
            <p class="text-sm sm:text-base text-emerald-50/90 leading-relaxed">
              কোনো মধ্যস্বত্বভোগী বা কমিশন ছাড়াই সরাসরি বাংলাদেশের কৃষকদের কাছ থেকে টাটকা ধান, চাল, আলু, পেঁয়াজ, ফল ও শাকসবজি কিনুন। অর্ডার করলে তাৎক্ষণিক রিয়েল-টাইম নোটিফিকেশন যায় কৃষকের কাছে।
            </p>
            <div class="flex flex-wrap gap-3 pt-1">
              <button onclick="window.kbApp.navigate('browse')" class="px-5 py-3 rounded-2xl bg-white text-[#105B34] font-bold text-sm shadow hover:bg-emerald-50">
                🌾 ফসল খুঁজুন
              </button>
              ${
                !isBuyer && !isAdmin
                  ? `<button onclick="window.kbApp.goSell()" class="px-5 py-3 rounded-2xl bg-amber-400 text-[#105B34] font-bold text-sm shadow hover:bg-amber-300">
                      👨‍🌾 কৃষক হিসেবে বিক্রি করুন
                    </button>`
                  : ""
              }
              <button onclick="window.kbApp.navigate('farmers')" class="px-4 py-3 rounded-2xl border border-white/40 text-white font-semibold text-sm hover:bg-white/10">
                কৃষকদের দেখুন
              </button>
            </div>
          </div>
          <div class="flex justify-center">
            <img src="${ASSET_BASE}showcase.png" alt="কৃষি বন্ধু প্ল্যাটফর্ম" class="rounded-2xl shadow-2xl border-2 border-white/25 max-h-72 object-cover w-full" />
          </div>
        </div>

        <!-- Quick Stats Strip -->
        <div class="bg-black/15 border-t border-white/15 px-6 py-3 grid grid-cols-2 sm:grid-cols-4 gap-3 text-center text-xs sm:text-sm">
          <div><span class="font-bold block text-base">${farmers.length}</span>নিবন্ধিত কৃষক</div>
          <div><span class="font-bold block text-base">${activeProducts.length}</span>সক্রিয় ফসল</div>
          <div><span class="font-bold block text-base">৬৪ জেলা</span>সারা বাংলাদেশ</div>
          <div><span class="font-bold block text-base">০%</span>কমিশন ফি</div>
        </div>
      </section>

      <!-- Search Box -->
      <section class="bg-white rounded-3xl p-4 sm:p-5 border border-emerald-100 shadow-sm flex flex-col sm:flex-row gap-3">
        <input id="home-search-input" type="text" value="${escapeHtml(state.filters.query)}"
          placeholder="ফসল, জেলা, উপজেলা বা কৃষকের নাম লিখে খুঁজুন (যেমন: মিনিকেট চাল, কুষ্টিয়া, বগুড়া)..."
          class="flex-1 px-4 py-3 rounded-2xl border border-emerald-200 focus:outline-none focus:ring-2 focus:ring-[#168447] text-sm" />
        <button onclick="window.kbApp.applyHomeSearch()" class="px-6 py-3 rounded-2xl bg-[#168447] text-white font-bold text-sm hover:bg-[#105B34]">
          🔍 অনুসন্ধান করুন
        </button>
      </section>

      <!-- Categories Grid -->
      <section class="space-y-3">
        <div class="flex items-center justify-between">
          <h2 class="text-lg sm:text-xl font-bold text-[#105B34]">জনপ্রিয় কৃষিপণ্য ক্যাটাগরি (২৪টি)</h2>
          <button onclick="window.kbApp.navigate('browse')" class="text-sm font-semibold text-[#168447] hover:underline">সব দেখুন →</button>
        </div>
        <div class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-3">
          ${topCats
            .map(
              (c) => `
            <button onclick="window.kbApp.filterByCategory('${escapeHtml(c.id)}')"
              class="bg-white hover:bg-[#EAF7EF] border border-emerald-100 rounded-2xl p-3 text-center transition shadow-sm">
              <div class="text-2xl mb-1">${escapeHtml(c.emoji || "🌱")}</div>
              <div class="text-xs font-bold text-[#202820] truncate">${escapeHtml(c.nameBn)}</div>
            </button>
          `
            )
            .join("")}
        </div>
      </section>

      <!-- Live Products Grid -->
      <section class="space-y-4">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg sm:text-xl font-bold text-[#105B34]">সাম্প্রতিক তাজা ফসল (সরাসরি কৃষকের খামার থেকে)</h2>
            <p class="text-xs text-gray-500">অ্যান্ড্রয়েড অ্যাপ ও ওয়েবসাইটে একসাথে লাইভ</p>
          </div>
          <button onclick="window.kbApp.navigate('browse')" class="text-sm font-semibold text-[#168447] hover:underline">আরও দেখুন →</button>
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
            : `<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">${activeProducts.slice(0, 8).map(renderProductCard).join("")}</div>`
        }
      </section>

      ${renderAdPlacement("feed")}

      <!-- Zero Middleman Promise -->
      <section class="bg-[#EAF7EF] rounded-3xl p-6 sm:p-8 border border-emerald-200 grid sm:grid-cols-3 gap-6">
        <div>
          <div class="text-2xl mb-2">🤝</div>
          <h3 class="font-bold text-[#105B34] mb-1">১০০% দালালমুক্ত</h3>
          <p class="text-xs text-gray-700">কৃষক নিজেই দাম নির্ধারণ করেন এবং ক্রেতার সাথে সরাসরি কথা বলে লেনদেন করেন।</p>
        </div>
        <div>
          <div class="text-2xl mb-2">🔔</div>
          <h3 class="font-bold text-[#105B34] mb-1">তাৎক্ষণিক অর্ডার নোটিফিকেশন</h3>
          <p class="text-xs text-gray-700">ওয়েব বা অ্যাপ থেকে অর্ডার করলেই সাথে সাথে কৃষকের কাছে রিয়েল-টাইম নোটিফিকেশন পৌঁছায়।</p>
        </div>
        <div>
          <div class="text-2xl mb-2">🗺️</div>
          <h3 class="font-bold text-[#105B34] mb-1">৮ বিভাগ ও ৬৪ জেলা</h3>
          <p class="text-xs text-gray-700">কুষ্টিয়া থেকে যাত্রা শুরু করে এখন বাংলাদেশের প্রতিটি জেলার কৃষক ও ক্রেতা সংযুক্ত।</p>
        </div>
      </section>
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
  const distOptions = f.division ? BD_DISTRICTS.filter((d) => d.divisionBn === f.division) : BD_DISTRICTS;
  const upzOptions = f.district ? BD_DISTRICTS.find((d) => d.districtBn === f.district)?.upazilas || [] : [];
  const unionOptions = getUnionOptions(f.district, f.upazila);
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
          placeholder="ফসল বা কৃষক খুঁজুন..." class="px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />

        <select onchange="window.kbApp.setFilter('division', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="">সব বিভাগ (৮টি)</option>
          ${BD_DIVISIONS.map((d) => `<option value="${escapeHtml(d)}" ${f.division === d ? "selected" : ""}>${escapeHtml(d)} বিভাগ</option>`).join("")}
        </select>

        <select onchange="window.kbApp.setFilter('district', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="">সব জেলা (৬৪টি)</option>
          ${distOptions.map((d) => `<option value="${escapeHtml(d.districtBn)}" ${f.district === d.districtBn ? "selected" : ""}>${escapeHtml(d.districtBn)}</option>`).join("")}
        </select>

        <select onchange="window.kbApp.setFilter('upazila', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white" ${!f.district ? "disabled" : ""}>
          <option value="">সব উপজেলা</option>
          ${upzOptions.map((u) => `<option value="${escapeHtml(u)}" ${f.upazila === u ? "selected" : ""}>${escapeHtml(u)}</option>`).join("")}
        </select>

        <select onchange="window.kbApp.setFilter('union', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white" ${!f.upazila && !unionOptions.length ? "disabled" : ""}>
          <option value="">সব ইউনিয়ন</option>
          ${unionOptions.map((un) => `<option value="${escapeHtml(un)}" ${f.union === un ? "selected" : ""}>${escapeHtml(un)}</option>`).join("")}
        </select>

        <select onchange="window.kbApp.setFilter('categoryId', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="">সব ক্যাটাগরি</option>
          ${state.categories.map((c) => `<option value="${escapeHtml(c.id)}" ${f.categoryId === c.id ? "selected" : ""}>${escapeHtml(c.emoji || "")} ${escapeHtml(c.nameBn)}</option>`).join("")}
        </select>

        <select onchange="window.kbApp.setFilter('wholesaleOnly', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="" ${f.wholesaleOnly === "" ? "selected" : ""}>সব ধরন (পাইকারি/খুচরা)</option>
          <option value="true" ${f.wholesaleOnly === "true" ? "selected" : ""}>পাইকারি</option>
          <option value="false" ${f.wholesaleOnly === "false" ? "selected" : ""}>খুচরা</option>
        </select>

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
          : `<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">${filtered.map(renderProductCard).join("")}</div>`
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
            ${prod.isWholesale ? `<span class="px-3 py-1 rounded-full bg-amber-100 text-amber-800 text-xs font-bold">পাইকারি</span>` : ""}
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
  const distOptions = f.division ? BD_DISTRICTS.filter((d) => d.divisionBn === f.division) : BD_DISTRICTS;
  const upzOptions = f.district ? BD_DISTRICTS.find((d) => d.districtBn === f.district)?.upazilas || [] : [];
  const unionOptions = getUnionOptions(f.district, f.upazila);
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
          ${BD_DIVISIONS.map((d) => `<option value="${escapeHtml(d)}" ${f.division === d ? "selected" : ""}>${escapeHtml(d)}</option>`).join("")}
        </select>
        <select onchange="window.kbApp.setFilter('district', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
          <option value="">সব জেলা</option>
          ${distOptions.map((d) => `<option value="${escapeHtml(d.districtBn)}" ${f.district === d.districtBn ? "selected" : ""}>${escapeHtml(d.districtBn)}</option>`).join("")}
        </select>
        <select onchange="window.kbApp.setFilter('upazila', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white" ${!f.district ? "disabled" : ""}>
          <option value="">সব উপজেলা</option>
          ${upzOptions.map((u) => `<option value="${escapeHtml(u)}" ${f.upazila === u ? "selected" : ""}>${escapeHtml(u)}</option>`).join("")}
        </select>
        <select onchange="window.kbApp.setFilter('union', this.value)" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white" ${!f.upazila && !unionOptions.length ? "disabled" : ""}>
          <option value="">সব ইউনিয়ন</option>
          ${unionOptions.map((un) => `<option value="${escapeHtml(un)}" ${f.union === un ? "selected" : ""}>${escapeHtml(un)}</option>`).join("")}
        </select>
      </div>

      ${
        farmers.length === 0
          ? `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200">এখনো কোনো কৃষক পাওয়া যায়নি।</div>`
          : `<div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
              ${farmers
                .map((u) => {
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
                })
                .join("")}
            </div>`
      }
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
          <input name="password" type="password" required placeholder="******" class="w-full px-4 py-3 rounded-xl border border-emerald-200 text-sm" />
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
        <p>নিবন্ধনের পর বিকাশ বা নগদে ৳${FIXED_ACTIVATION_FEE_BDT} পেমেন্ট তথ্য জমা দিয়ে অ্যাডমিন অনুমোদন পেলে ২ বছরের জন্য সকল ফসল কেনাবেচা চালু হবে।</p>
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
            <input name="password" type="password" minlength="6" required class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold text-gray-700 block mb-1">পাসওয়ার্ড নিশ্চিত করুন *</label>
            <input name="confirmPassword" type="password" minlength="6" required class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
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
            ? `<button onclick="window.kbApp.navigate('activation')" class="px-4 py-2.5 rounded-xl bg-[#168447] text-white text-xs font-bold hover:bg-[#105B34]">
                ${isMarketplaceActive(user) ? "🔄 মেয়াদ ও পেমেন্ট ইতিহাস" : "⚡ এখনই অ্যাকাউন্ট সক্রিয় করুন"}
              </button>`
            : ""
        }
      </div>

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
  const hasPending = myPayments.some((p) => p.status === "PENDING");
  const isRenewal = user.activationDate > 0 || st === "EXPIRED" || st === "ACTIVE";

  return `
    <div class="max-w-3xl mx-auto space-y-6">
      <div class="bg-gradient-to-br from-[#105B34] to-[#168447] text-white rounded-3xl p-6 sm:p-8 shadow-md space-y-3">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="px-3 py-1 rounded-full bg-white/15 text-xs font-bold">🔐 অ্যাকাউন্ট অ্যাক্টিভেশন ও ২ বছর মেয়াদ</span>
          <span class="px-3 py-1 rounded-full text-xs font-bold ${stMeta.color}">${stMeta.labelBn}</span>
        </div>
        <h1 class="text-2xl font-extrabold">নির্ধারিত ফি: ৳${FIXED_ACTIVATION_FEE_BDT} (${FIXED_VALIDITY_YEARS} বছরের মেয়াদ)</h1>
        <p class="text-xs sm:text-sm text-emerald-50/90">${escapeHtml(settings.paymentInstructions || DEFAULT_PAYMENT_SETTINGS.paymentInstructions)}</p>
        ${user.expiryDate > 0 ? `<div class="text-xs bg-white/15 rounded-2xl px-4 py-2 inline-block">বর্তমান মেয়াদ: ${formatDateBn(user.expiryDate)} (${daysUntilExpiry(user) ?? 0} দিন বাকি)</div>` : ""}
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
        <h2 class="text-lg font-bold text-[#105B34]">${isRenewal ? "🔄 নবায়ন পেমেন্ট তথ্য জমা দিন" : "💳 অ্যাক্টিভেশন পেমেন্ট তথ্য জমা দিন"}</h2>
        ${
          hasPending
            ? `<div class="bg-sky-50 border border-sky-200 rounded-2xl p-4 text-xs sm:text-sm text-sky-900 font-semibold">
                ⏳ আপনার একটি পেমেন্ট বর্তমানে অ্যাডমিন যাচাইয়ের জন্য অপেক্ষমাণ আছে। যাচাই সম্পন্ন হলেই নোটিফিকেশন পাবেন।
              </div>`
            : `<form onsubmit="window.kbApp.handleActivationPaymentSubmit(event)" class="space-y-3.5">
                <input type="hidden" name="isRenewal" value="${isRenewal ? "1" : "0"}" />
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
                    <input name="amount" value="${FIXED_ACTIVATION_FEE_BDT}" readonly class="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 bg-gray-50 text-sm font-bold text-[#105B34]" />
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
                  ✓ ৳${FIXED_ACTIVATION_FEE_BDT} পেমেন্ট তথ্য জমা দিন
                </button>
              </form>`
        }
      </div>

      <!-- Submission History -->
      <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm space-y-3">
        <h3 class="font-bold text-[#105B34]">📜 আমার পেমেন্ট জমাদানের ইতিহাস (${myPayments.length})</h3>
        ${
          !myPayments.length
            ? `<p class="text-xs text-gray-500">এখনো কোনো অ্যাক্টিভেশন পেমেন্ট জমা দেওয়া হয়নি।</p>`
            : `<div class="space-y-2.5">
                ${myPayments
                  .map((p) => {
                    const pMeta = ACTIVATION_PAYMENT_STATUS_META[p.status] || ACTIVATION_PAYMENT_STATUS_META.PENDING;
                    return `<div class="p-3.5 rounded-2xl bg-[#F7FAF7] border border-emerald-100 text-xs flex flex-wrap justify-between gap-2">
                      <div>
                        <div class="font-bold text-[#105B34]">${escapeHtml(p.paymentMethod)} • TxID: ${escapeHtml(p.transactionId)} • ${formatBdt(p.amount)}</div>
                        <div class="text-gray-600">প্রেরক: ${escapeHtml(p.senderMobile)} • ${new Date(p.submittedAt).toLocaleString("bn-BD")}</div>
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
  const step1 = true;
  const step2 = !["PENDING", "REJECTED", "CANCELLED"].includes(o.status);
  const step3 = Boolean(o.transactionReference || o.paymentReceiptUri || ["SUBMITTED", "VERIFIED", "PAID"].includes(o.paymentStatus));
  const step4 = Boolean(o.farmerPaymentConfirmed && o.adminPaymentVerified);
  const step5 = ["CONFIRMED", "COMPLETED"].includes(o.status);
  const steps = [
    ["১. অর্ডার", step1],
    ["২. কৃষক গ্রহণ", step2],
    ["৩. পেমেন্ট জমা", step3],
    ["৪. কৃষক+অ্যাডমিন যাচাই", step4],
    ["৫. নিশ্চিত/সম্পন্ন", step5]
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
  const farmerBadge = o.farmerPaymentConfirmed
    ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">✓ কৃষক টাকা পেয়েছেন</span>`
    : `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-200">⏳ কৃষকের টাকা প্রাপ্তি বাকি</span>`;
  const adminBadge = o.adminPaymentVerified
    ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">✓ অ্যাডমিন পেমেন্ট যাচাইকৃত</span>`
    : o.paymentStatus === "REJECTED"
    ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-red-100 text-red-700">✕ পেমেন্ট প্রত্যাখ্যাত</span>`
    : `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-sky-50 text-sky-800 border border-sky-200">⏳ অ্যাডমিন যাচাই বাকি</span>`;
  return `
    <div class="flex flex-wrap items-center gap-1.5 pt-1">
      ${farmerBadge}
      ${adminBadge}
      ${o.transactionReference ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-gray-100 text-gray-800">TxID: ${escapeHtml(o.transactionReference)}</span>` : ""}
      ${o.paymentReceiptUri ? `<button type="button" onclick="window.kbApp.openReceiptPreviewModal('${escapeHtml(o.id)}', 'order')" class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#EAF7EF] text-[#105B34] border border-emerald-200">🖼️ রসিদ দেখুন</button>` : ""}
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
              <h1 class="text-2xl font-bold text-[#105B34]">👨‍🌾 কৃষক ড্যাশবোর্ড</h1>
              <span class="px-2.5 py-0.5 rounded-full text-xs font-bold ${stMeta.color}">${stMeta.labelBn}</span>
              ${user.verificationStatus === "VERIFIED" ? `<span class="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800">✓ যাচাইকৃত</span>` : ""}
            </div>
            <p class="text-xs text-gray-600">${escapeHtml(user.fullName)} • 📍 ${escapeHtml(user.unionName ? user.unionName + ", " : "")}${escapeHtml(user.upazila)}, ${escapeHtml(user.district)}, ${escapeHtml(user.division)}</p>
          </div>
        </div>
        <div class="flex flex-wrap gap-2 items-center">
          <div class="px-4 py-2 rounded-2xl bg-[#EAF7EF] text-center"><div class="font-extrabold text-[#105B34]">${myProducts.length}</div><div class="text-[11px]">আমার ফসল</div></div>
          <div class="px-4 py-2 rounded-2xl bg-[#EAF7EF] text-center"><div class="font-extrabold text-[#105B34]">${myOrders.length}</div><div class="text-[11px]">মোট অর্ডার</div></div>
          <div class="px-4 py-2 rounded-2xl bg-[#EAF7EF] text-center"><div class="font-extrabold text-[#105B34]">${formatBdt(totalSales)}</div><div class="text-[11px]">মোট বিক্রয়</div></div>
          <button onclick="window.kbApp.navigate('profile')" class="px-3.5 py-2 rounded-2xl border border-emerald-200 text-xs font-bold text-[#105B34] hover:bg-[#EAF7EF]">✏️ প্রোফাইল</button>
        </div>
      </div>

      <!-- Tabs -->
      <div class="flex flex-wrap gap-2">
        <button onclick="window.kbApp.setFarmerTab('orders')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "orders" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">📦 প্রাপ্ত অর্ডার (${myOrders.length})</button>
        <button onclick="window.kbApp.setFarmerTab('products')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "products" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">🌾 আমার ফসল (${myProducts.length})</button>
        <button onclick="window.kbApp.setFarmerTab('add')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "add" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">+ নতুন ফসল যোগ করুন</button>
      </div>

      ${tab === "orders" ? renderFarmerOrdersTab(myOrders, active) : tab === "products" ? renderFarmerProductsTab(myProducts, active) : renderAddProductTab(user, active)}
    </div>
  `;
}

function renderFarmerOrdersTab(myOrders, active) {
  if (!myOrders.length) {
    return `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200">এখনো কোনো অর্ডার আসেনি। ক্রেতা অর্ডার করলেই এখানে ও নোটিফিকেশনে সাথে সাথে দেখাবে।</div>`;
  }
  return `
    <div class="space-y-4">
      ${myOrders
        .map((o) => {
          const meta = ORDER_STATUS_META[o.status] || ORDER_STATUS_META.PENDING;
          const canConfirmMoney =
            active &&
            !o.farmerPaymentConfirmed &&
            o.status !== "PENDING" &&
            !["CANCELLED", "REJECTED"].includes(o.status);
          const dualConfirmed = Boolean(o.farmerPaymentConfirmed && o.adminPaymentVerified);
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
                <div>মোট মূল্য: <strong class="text-[#168447]">${formatBdt(o.grandTotal)}</strong> (${escapeHtml(o.paymentMethod)})</div>
                <div class="sm:col-span-2">ঠিকানা: ${escapeHtml(o.deliveryAddress)}</div>
              </div>
              ${renderOrderSecuritySteps(o)}
              ${renderOrderPaymentBadgeRow(o)}
              <div class="flex flex-wrap gap-2 pt-1">
                ${
                  o.status === "PENDING"
                    ? `
                      <button ${!active ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'ACCEPTED')" class="px-3.5 py-2 rounded-xl bg-[#168447] disabled:opacity-50 text-white text-xs font-bold">✓ অর্ডার গ্রহণ করুন</button>
                      <button ${!active ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'REJECTED')" class="px-3.5 py-2 rounded-xl bg-red-600 disabled:opacity-50 text-white text-xs font-bold">✕ প্রত্যাখ্যান করুন</button>
                    `
                    : ""
                }
                ${
                  canConfirmMoney
                    ? `<button onclick="window.kbApp.farmerConfirmPaymentReceived('${escapeHtml(o.id)}')" class="px-3.5 py-2 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold">💰 টাকা বুঝে পেয়েছি</button>`
                    : ""
                }
                ${o.status === "ACCEPTED" ? `<button ${!active ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'PROCESSING')" class="px-3.5 py-2 rounded-xl bg-sky-600 disabled:opacity-50 text-white text-xs font-bold">প্রস্তুত হচ্ছে</button>` : ""}
                ${o.status === "PROCESSING" ? `<button ${!active ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'READY_FOR_PICKUP')" class="px-3.5 py-2 rounded-xl bg-teal-600 disabled:opacity-50 text-white text-xs font-bold">সংগ্রহের জন্য প্রস্তুত</button>` : ""}
                ${["READY_FOR_PICKUP", "READY"].includes(o.status) ? `<button ${!active ? "disabled" : ""} onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'SHIPPED')" class="px-3.5 py-2 rounded-xl bg-indigo-600 disabled:opacity-50 text-white text-xs font-bold">পথে পাঠানো হয়েছে</button>` : ""}
                ${
                  ["READY_FOR_PICKUP", "READY", "SHIPPED", "DELIVERED", "CONFIRMED"].includes(o.status)
                    ? `<button ${!active || !dualConfirmed ? "disabled" : ""} title="${dualConfirmed ? "" : "কৃষকের টাকা প্রাপ্তি ও অ্যাডমিন যাচাই উভয়ই প্রয়োজন"}" onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'COMPLETED')" class="px-3.5 py-2 rounded-xl bg-emerald-700 disabled:opacity-40 text-white text-xs font-bold">✓ সম্পন্ন হয়েছে</button>`
                    : ""
                }
              </div>
              ${
                !dualConfirmed && !["PENDING", "CANCELLED", "REJECTED", "COMPLETED"].includes(o.status)
                  ? `<p class="text-[11px] text-amber-800 bg-amber-50 rounded-xl px-3 py-1.5 border border-amber-200">🔒 নিরাপত্তা নিয়ম: ক্রেতার পেমেন্টের পর আপনার "টাকা বুঝে পেয়েছি" নিশ্চিতকরণ এবং অ্যাডমিনের পেমেন্ট যাচাই সম্পন্ন হলেই অর্ডার Confirmed / Completed হবে।</p>`
                  : ""
              }
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
        .map(
          (p) => `
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex justify-between items-start">
              <div>
                <h3 class="font-bold text-base text-[#105B34]">${escapeHtml(p.nameBn)}</h3>
                <p class="text-xs text-gray-500">${escapeHtml(p.categoryNameBn)} • ${escapeHtml(p.district)} • ১ মণ = ${MON_TO_KG} কেজি</p>
              </div>
              <span class="px-2 py-0.5 rounded-full text-[11px] font-bold ${p.status === "ACTIVE" ? "bg-emerald-100 text-emerald-800" : "bg-gray-200 text-gray-700"}">${escapeHtml(p.status)}</span>
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
            <button ${!active ? "disabled" : ""} onclick="window.kbApp.updateFarmerPriceAndStock('${escapeHtml(p.id)}', document.getElementById('price_${escapeHtml(p.id)}').value, document.getElementById('stock_${escapeHtml(p.id)}').value)"
              class="w-full py-2.5 rounded-xl bg-[#168447] disabled:opacity-50 text-white text-xs font-bold hover:bg-[#105B34]">
              আপডেট সংরক্ষণ করুন
            </button>
          </div>
        `
        )
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
          <label class="flex items-center gap-2"><input type="checkbox" name="isWholesale" ${!active ? "disabled" : ""} /> পাইকারি বিক্রি</label>
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
        <h1 class="text-xl font-bold text-[#105B34]">আপনার কার্ট খালি</h1>
        <p class="text-sm text-gray-600">তাজা কৃষিপণ্য মণ হিসেবে কার্টে যোগ করে সরাসরি কৃষকের কাছ থেকে অর্ডার করুন।</p>
        <button onclick="window.kbApp.navigate('browse')" class="px-5 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">ফসল খুঁজুন</button>
      </div>
    `;
  }

  const productTotal = state.cart.reduce((s, c) => s + Number(c.pricePerUnit || 0) * Number(c.quantity || 0), 0);
  const totalMon = state.cart.reduce((s, c) => s + Number(c.quantity || 0), 0);
  const totalKg = totalMon * MON_TO_KG;
  const active = user && isMarketplaceActive(user);

  return `
    <div class="space-y-4">
      ${user ? renderAccountStatusBanner(user) : ""}
      <div class="grid lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2 space-y-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-2xl font-bold text-[#105B34]">🛒 আপনার শপিং কার্ট (${state.cart.length}টি আইটেম)</h1>
            <span class="px-3 py-1 rounded-full bg-[#EAF7EF] text-[#105B34] text-xs font-bold">মোট: ${formatQty(totalMon)} মণ (${formatQty(totalKg)} কেজি)</span>
          </div>
          ${state.cart
            .map((c) => {
              const kg = Number(c.quantity || 0) * MON_TO_KG;
              return `
                <div class="bg-white rounded-2xl p-4 border border-emerald-100 flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <h3 class="font-bold text-sm text-[#202820]">${escapeHtml(c.productNameBn)}</h3>
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
          <h2 class="text-lg font-bold text-[#105B34]">চেকআউট ও অর্ডার জমা</h2>
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
                <label class="text-xs font-bold">ডেলিভারি ঠিকানা (হোম ডেলিভারির ক্ষেত্রে)</label>
                <div class="flex gap-1">
                  <button type="button" onclick="window.kbApp.detectBrowserLocation('checkout')" class="px-2 py-0.5 rounded-lg bg-[#EAF7EF] text-[#105B34] text-[11px] font-bold">📍 GPS অটো</button>
                  <button type="button" onclick="window.kbApp.openLocationMapModal('checkout')" class="px-2 py-0.5 rounded-lg border border-emerald-200 text-[#105B34] text-[11px] font-bold">🗺️ ম্যাপ</button>
                </div>
              </div>
              <input id="checkout-delivery-address" name="deliveryAddress" value="${escapeHtml(user ? `${user.village ? user.village + ", " : ""}${user.unionName ? user.unionName + ", " : ""}${user.upazila}, ${user.district}` : "")}" placeholder="বাড়ি, গ্রাম, ইউনিয়ন, উপজেলা, জেলা" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">সম্ভাব্য পেমেন্ট মাধ্যম</label>
              <select name="paymentMethod" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 bg-white">
                <option value="CASH_ON_PICKUP">নগদ (খামারে সংগ্রহ / ক্যাশ অন ডেলিভারি)</option>
                <option value="BKASH">বিকাশ (bKash)</option>
                <option value="NAGAD">নগদ (Nagad)</option>
                <option value="BANK_TRANSFER">ব্যাংক ট্রান্সফার</option>
              </select>
            </div>
            <div class="border-t border-emerald-100 pt-3 space-y-1">
              <div class="flex justify-between text-xs text-gray-600">
                <span>মোট পরিমাণ</span>
                <span>${formatQty(totalMon)} মণ (${formatQty(totalKg)} কেজি)</span>
              </div>
              <div class="flex justify-between font-bold text-base">
                <span>মোট পণ্যের দাম</span>
                <span class="text-[#168447]">${formatBdt(productTotal)}</span>
              </div>
            </div>
            <p class="text-[11px] text-gray-500">১. অর্ডার জমার পর কৃষক গ্রহণ করলে পেমেন্ট ও রসিদ জমা দিন। ২. কৃষকের টাকা প্রাপ্তি ও অ্যাডমিন যাচাইয়ের পর অর্ডার চূড়ান্ত নিশ্চিত হবে।</p>
            <button type="submit" ${user && !active ? "disabled" : ""} class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] disabled:opacity-50 text-white font-bold">অর্ডার জমা দিন</button>
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
              ? `🛍️ আমার ক্রয় অর্ডার ও পেমেন্ট (${myOrders.length})`
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
                const meta = ORDER_STATUS_META[o.status] || ORDER_STATUS_META.PENDING;
                const isBuyer = o.buyerId === user.id;
                const isFarmer = o.farmerId === user.id;
                const canCancel = isBuyer && o.status === "PENDING";
                const canSubmitPayment =
                  active &&
                  isBuyer &&
                  o.status !== "PENDING" &&
                  !["CANCELLED", "REJECTED", "COMPLETED"].includes(o.status) &&
                  !o.adminPaymentVerified;
                const canFarmerConfirmMoney =
                  active &&
                  isFarmer &&
                  !o.farmerPaymentConfirmed &&
                  o.status !== "PENDING" &&
                  !["CANCELLED", "REJECTED"].includes(o.status);
                const dualConfirmed = Boolean(o.farmerPaymentConfirmed && o.adminPaymentVerified);
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
                      <div>মোট: <strong class="text-[#168447]">${formatBdt(o.grandTotal)}</strong> (${escapeHtml(o.paymentMethod)})</div>
                    </div>
                    ${renderOrderSecuritySteps(o)}
                    ${renderOrderPaymentBadgeRow(o)}
                    <div class="flex flex-wrap gap-2 pt-1">
                      ${
                        isBuyer && o.status === "PENDING"
                          ? `<span class="px-3 py-1.5 rounded-xl bg-amber-50 text-amber-900 border border-amber-200 text-xs font-semibold">⏳ কৃষক অর্ডারটি Accept করলে পেমেন্ট জমা দিতে পারবেন।</span>`
                          : ""
                      }
                      ${
                        canSubmitPayment
                          ? `<button onclick="window.kbApp.openOrderPaymentModal('${escapeHtml(o.id)}')" class="px-3.5 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">💳 ${o.paymentStatus === "SUBMITTED" ? "পেমেন্ট তথ্য আপডেট করুন" : "পেমেন্ট ও রসিদ জমা দিন"}</button>`
                          : ""
                      }
                      ${
                        canFarmerConfirmMoney
                          ? `<button onclick="window.kbApp.farmerConfirmPaymentReceived('${escapeHtml(o.id)}')" class="px-3.5 py-1.5 rounded-xl bg-amber-600 text-white text-xs font-bold">💰 টাকা বুঝে পেয়েছি</button>`
                          : ""
                      }
                      ${
                        canBuyerComplete
                          ? `<button ${!dualConfirmed ? "disabled" : ""} title="${dualConfirmed ? "" : "কৃষকের টাকা প্রাপ্তি ও অ্যাডমিন যাচাই উভয়ই প্রয়োজন"}" onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'COMPLETED')" class="px-3.5 py-1.5 rounded-xl bg-emerald-700 disabled:opacity-40 text-white text-xs font-bold">✓ ডেলিভারি সম্পন্ন</button>`
                          : ""
                      }
                      ${canCancel ? `<button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'CANCELLED')" class="px-3 py-1.5 rounded-xl bg-red-50 text-red-700 border border-red-200 text-xs font-bold">অর্ডার বাতিল করুন</button>` : ""}
                      ${canReview ? `<button onclick="window.kbApp.openReviewModal('${escapeHtml(o.id)}')" class="px-3 py-1.5 rounded-xl bg-amber-50 text-amber-800 border border-amber-200 text-xs font-bold">★ রিভিউ দিন</button>` : ""}
                      <button onclick="window.kbApp.openComplaintModal('${escapeHtml(o.id)}')" class="px-3 py-1.5 rounded-xl border border-gray-200 text-gray-700 text-xs font-semibold">অভিযোগ জানান</button>
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
  const orderPayVerifyCount = state.orders.filter((o) => o.paymentStatus === "SUBMITTED" && !o.adminPaymentVerified && !["CANCELLED", "REJECTED"].includes(o.status)).length;
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
    ["orders", `অর্ডার${orderPayVerifyCount ? ` (যাচাই ${orderPayVerifyCount})` : ` (${state.orders.length})`}`],
    ["payment-settings", "⚙️ পেমেন্ট সেটিংস"],
    ["notifications", "🔔 নোটিফিকেশন পাঠান"],
    ["categories", "ক্যাটাগরি ও এলাকা"],
    ["complaints", `অভিযোগ (${state.complaints.length})`],
    ["ads", "💰 বিজ্ঞাপন ও সব নেটওয়ার্ক"],
    ["audit", "অডিট লগ"]
  ];

  let bodyHtml = "";
  if (tab === "overview") {
    const entries = Array.from(districtTotals.entries()).sort((a, b) => b[1] - a[1]);
    bodyHtml = `
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">অপেক্ষমাণ অ্যাক্টিভেশন পেমেন্ট</div><div class="text-2xl font-extrabold text-amber-700">${pendingActCount}</div><div class="text-[11px] text-gray-500 mt-1">মোট আবেদন: ${state.activationPayments.length}</div></div>
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">সক্রিয় অ্যাকাউন্ট (২ বছর)</div><div class="text-2xl font-extrabold text-[#168447]">${activeUsersCount}</div><div class="text-[11px] text-gray-500 mt-1">মেয়াদোত্তীর্ণ: ${expiredUsersCount} • স্থগিত: ${suspendedUsersCount}</div></div>
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">অর্ডার পেমেন্ট যাচাই বাকি</div><div class="text-2xl font-extrabold text-sky-700">${orderPayVerifyCount}</div><div class="text-[11px] text-gray-500 mt-1">মোট বৈধ অর্ডার: ${validOrders.length}</div></div>
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
                            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#EAF7EF] text-[#105B34]">${p.userRole === "FARMER" ? "👨‍🌾 কৃষক" : "🛍️ ক্রেতা"}</span>
                            <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${pMeta.color}">${pMeta.labelBn}</span>
                            ${p.isRenewal ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-100 text-indigo-800">🔄 নবায়ন</span>` : ""}
                            ${isDupTx ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-red-600 text-white">⚠️ ডুপ্লিকেট TxID</span>` : ""}
                          </div>
                          <div class="text-gray-700 font-semibold">${escapeHtml(p.paymentMethod)} • TxID: <span class="font-mono text-[#105B34]">${escapeHtml(p.transactionId)}</span> • পরিমাণ: <strong>${formatBdt(p.amount)}</strong> (${p.validityYears || FIXED_VALIDITY_YEARS} বছর)</div>
                          <div class="text-gray-500">প্রেরক নম্বর: ${escapeHtml(p.senderMobile)} • জমা: ${new Date(p.submittedAt).toLocaleString("bn-BD")}</div>
                          ${p.adminNotes ? `<div class="text-red-700 font-semibold">নোট: ${escapeHtml(p.adminNotes)}</div>` : ""}
                        </div>
                        <div class="flex flex-wrap gap-2">
                          ${p.receiptUri ? `<button onclick="window.kbApp.openReceiptPreviewModal('${escapeHtml(p.id)}', 'activation')" class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold border border-emerald-200">🖼️ রসিদ দেখুন</button>` : ""}
                          ${
                            p.status !== "APPROVED"
                              ? `<button onclick="window.kbApp.adminApproveActivationPayment('${escapeHtml(p.id)}')" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">✓ অনুমোদন (২ বছর)</button>
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
                          </div>
                          <div class="text-xs text-gray-600">${escapeHtml(person.unionName ? person.unionName + ", " : "")}${escapeHtml(person.upazila)}, ${escapeHtml(person.district)}, ${escapeHtml(person.division)} • NID: ${escapeHtml(person.nidMasked || "N/A")}</div>
                          <div class="text-[11px] text-gray-500">${person.expiryDate > 0 ? `মেয়াদ: ${formatDateBn(person.expiryDate)} (${daysUntilExpiry(person) ?? 0} দিন বাকি)` : "মেয়াদ এখনো শুরু হয়নি"}</div>
                        </div>
                      </div>
                      <div class="flex flex-wrap gap-2">
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
  } else if (tab === "orders") {
    const oFilter = state.adminOrderFilter || "ALL";
    const filteredOrders =
      oFilter === "PAYMENT_VERIFY_REQ"
        ? state.orders.filter((o) => o.paymentStatus === "SUBMITTED" && !o.adminPaymentVerified && !["CANCELLED", "REJECTED"].includes(o.status))
        : state.orders;

    bodyHtml = `
      <div class="space-y-4">
        <div class="bg-white rounded-2xl p-4 border border-emerald-100 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 class="font-bold text-sm text-[#105B34]">অর্ডার ও পেমেন্ট নিরাপত্তা যাচাই</h3>
            <p class="text-xs text-gray-600">শুধুমাত্র কৃষকের টাকা প্রাপ্তি নিশ্চিতকরণ এবং অ্যাডমিনের পেমেন্ট যাচাই সম্পন্ন হলেই অর্ডার চূড়ান্ত Confirmed হবে।</p>
          </div>
          <div class="flex gap-2">
            <button onclick="window.kbApp.setAdminOrderFilter('ALL')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${oFilter === "ALL" ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">সব অর্ডার (${state.orders.length})</button>
            <button onclick="window.kbApp.setAdminOrderFilter('PAYMENT_VERIFY_REQ')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${oFilter === "PAYMENT_VERIFY_REQ" ? "bg-[#105B34] text-white" : "bg-sky-50 text-sky-900 border border-sky-200"}">পেমেন্ট যাচাই বাকি (${orderPayVerifyCount})</button>
          </div>
        </div>

        <div class="space-y-3">
          ${
            !filteredOrders.length
              ? `<div class="bg-white rounded-2xl p-8 text-center text-sm text-gray-500">এই ফিল্টারে কোনো অর্ডার নেই।</div>`
              : filteredOrders
                  .map((o) => {
                    const meta = ORDER_STATUS_META[o.status] || ORDER_STATUS_META.PENDING;
                    const canVerify = !["PENDING", "CANCELLED", "REJECTED"].includes(o.status);
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
                        ${
                          canVerify
                            ? `<div class="flex flex-wrap gap-2 pt-1">
                                ${!o.adminPaymentVerified ? `<button onclick="window.kbApp.adminVerifyOrderPayment('${escapeHtml(o.id)}', true)" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white font-bold">✓ পেমেন্ট যাচাই অনুমোদন</button>` : ""}
                                ${o.paymentStatus !== "REJECTED" ? `<button onclick="window.kbApp.adminVerifyOrderPayment('${escapeHtml(o.id)}', false, 'অ্যাডমিন যাচাইয়ে পেমেন্ট অমিল পাওয়া গেছে')" class="px-3 py-1.5 rounded-xl bg-red-600 text-white font-bold">✕ পেমেন্ট বাতিল</button>` : ""}
                              </div>`
                            : ""
                        }
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
    bodyHtml = `
      <form onsubmit="window.kbApp.handleAdminSaveAdConfig(event)" class="space-y-5">
        <div class="bg-emerald-50 border border-emerald-200 rounded-3xl p-5 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 class="text-lg font-bold text-[#105B34]">💰 ওয়েবসাইট ও মোবাইল অ্যাপ — সব অ্যাড নেটওয়ার্ক মনিটাইজেশন</h3>
            <p class="text-xs text-gray-700">এখান থেকে Google AdSense, Adsterra, Monetag, Ezoic/Custom HTML, Direct Sponsor এবং Android AdMob একসাথে নিয়ন্ত্রণ করুন (Firebase RTDB-তে তাৎক্ষণিক সেভ হবে)।</p>
          </div>
          <div class="flex flex-wrap gap-4 text-xs font-bold">
            <label class="flex items-center gap-2 bg-white px-3 py-2 rounded-xl border border-emerald-200"><input type="checkbox" name="adsEnabled" ${cfg.adsEnabled ? "checked" : ""} /> সব বিজ্ঞাপন চালু</label>
            <label class="flex items-center gap-2 bg-white px-3 py-2 rounded-xl border border-emerald-200"><input type="checkbox" name="showAdsLabel" ${cfg.showAdsLabel ? "checked" : ""} /> Sponsored ব্যাজ দেখান</label>
          </div>
        </div>

        <div class="grid md:grid-cols-2 gap-5">
          <!-- Google AdSense -->
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">1️⃣ Google AdSense (Website)</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="adsenseEnabled" ${cfg.adsenseEnabled ? "checked" : ""} /> সক্রিয়</label>
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
          </div>

          <!-- Android Google AdMob + Multi-Network Mobile Controls -->
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">2️⃣ মোবাইল অ্যাপ মনিটাইজেশন (AdMob + সব নেটওয়ার্ক)</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="androidAdsEnabled" ${cfg.androidAdsEnabled ? "checked" : ""} /> AdMob সক্রিয়</label>
            </div>
            <label class="text-xs font-bold flex items-center gap-1.5 bg-[#EAF7EF] px-3 py-2 rounded-xl border border-emerald-200"><input type="checkbox" name="mobileWebAdsEnabled" ${cfg.mobileWebAdsEnabled !== false ? "checked" : ""} /> মোবাইলে Multi-Network Ads (Adsterra/Monetag/AdSense/Custom) চালু</label>
            <div>
              <label class="text-xs font-bold block mb-1">মোবাইল অগ্রাধিকার নেটওয়ার্ক</label>
              <select name="mobileAdNetworkPriority" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs bg-white">
                <option value="ALL" ${(cfg.mobileAdNetworkPriority || "ALL") === "ALL" ? "selected" : ""}>সব নেটওয়ার্ক (All Networks)</option>
                <option value="ADMOB" ${cfg.mobileAdNetworkPriority === "ADMOB" ? "selected" : ""}>Google AdMob / Mediation</option>
                <option value="ADSTERRA" ${cfg.mobileAdNetworkPriority === "ADSTERRA" ? "selected" : ""}>Adsterra</option>
                <option value="MONETAG" ${cfg.mobileAdNetworkPriority === "MONETAG" ? "selected" : ""}>Monetag / PropellerAds</option>
                <option value="ADSENSE" ${cfg.mobileAdNetworkPriority === "ADSENSE" ? "selected" : ""}>Google AdSense</option>
                <option value="CUSTOM" ${cfg.mobileAdNetworkPriority === "CUSTOM" ? "selected" : ""}>Unity / AppLovin / Start.io / Custom HTML</option>
                <option value="SPONSOR" ${cfg.mobileAdNetworkPriority === "SPONSOR" ? "selected" : ""}>শুধুমাত্র সরাসরি স্পন্সর</option>
              </select>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Android Banner Ad Unit ID (ফাঁকা থাকলে Google Test ID)</label>
              <input name="androidBannerId" value="${escapeHtml(cfg.androidBannerId)}" placeholder="ca-app-pub-xxxxxxxxxxxxxxxx/xxxxxxxxxx" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Android Interstitial Ad Unit ID</label>
              <input name="androidInterstitialId" value="${escapeHtml(cfg.androidInterstitialId)}" placeholder="ca-app-pub-xxxxxxxxxxxxxxxx/xxxxxxxxxx" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">মোবাইল Direct Link / SmartLink URL</label>
              <input name="mobileDirectLinkUrl" value="${escapeHtml(cfg.mobileDirectLinkUrl || "")}" placeholder="https://..." class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">মোবাইল Custom HTML5 / Unity / AppLovin / Start.io ট্যাগ</label>
              <textarea name="mobileCustomAdHtml" rows="2" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs font-mono">${escapeHtml(cfg.mobileCustomAdHtml || "")}</textarea>
            </div>
          </div>

          <!-- Adsterra -->
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">3️⃣ Adsterra Ad Network</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="adsterraEnabled" ${cfg.adsterraEnabled ? "checked" : ""} /> সক্রিয়</label>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Adsterra Banner Key</label>
              <input name="adsterraBannerKey" value="${escapeHtml(cfg.adsterraBannerKey)}" placeholder="32-char Adsterra key" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Banner invoke.js URL</label>
              <input name="adsterraScriptUrl" value="${escapeHtml(cfg.adsterraScriptUrl)}" placeholder="https://www.topcreativeformat.com/.../invoke.js" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Social Bar / Popunder Script URL</label>
              <input name="adsterraSocialBarUrl" value="${escapeHtml(cfg.adsterraSocialBarUrl)}" placeholder="https://pl...effectivegatecpm.com/...js" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
          </div>

          <!-- Monetag / PropellerAds -->
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">4️⃣ Monetag / PropellerAds</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="monetagEnabled" ${cfg.monetagEnabled ? "checked" : ""} /> সক্রিয়</label>
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Monetag Zone ID</label>
              <input name="monetagZoneId" value="${escapeHtml(cfg.monetagZoneId)}" placeholder="Zone ID (e.g. 8123456)" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
            <div>
              <label class="text-xs font-bold block mb-1">Monetag Script Tag URL</label>
              <input name="monetagScriptUrl" value="${escapeHtml(cfg.monetagScriptUrl)}" placeholder="https://alwingulla.com/88/tag.min.js" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            </div>
          </div>
        </div>

        <!-- Ezoic / Media.net / Custom HTML + Direct Sponsor -->
        <div class="grid md:grid-cols-2 gap-5">
          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">5️⃣ Ezoic / Media.net / Infolinks / Custom Tag</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="customAdEnabled" ${cfg.customAdEnabled ? "checked" : ""} /> সক্রিয়</label>
            </div>
            <input name="customAdName" value="${escapeHtml(cfg.customAdName)}" placeholder="Network Name" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" />
            <div><label class="text-[11px] font-bold block mb-1">Top Banner HTML / Embed</label><textarea name="customTopHtml" rows="2" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs font-mono">${escapeHtml(cfg.customTopHtml)}</textarea></div>
            <div><label class="text-[11px] font-bold block mb-1">In-Feed Banner HTML / Embed</label><textarea name="customFeedHtml" rows="2" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs font-mono">${escapeHtml(cfg.customFeedHtml)}</textarea></div>
            <div><label class="text-[11px] font-bold block mb-1">Footer Banner HTML / Embed</label><textarea name="customFooterHtml" rows="2" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs font-mono">${escapeHtml(cfg.customFooterHtml)}</textarea></div>
          </div>

          <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
            <div class="flex items-center justify-between">
              <h4 class="font-bold text-[#105B34]">6️⃣ সরাসরি কৃষি স্পন্সর ব্যানার (Direct Ads)</h4>
              <label class="text-xs font-bold flex items-center gap-1.5"><input type="checkbox" name="sponsorEnabled" ${cfg.sponsorEnabled ? "checked" : ""} /> সক্রিয়</label>
            </div>
            <div><label class="text-[11px] font-bold block mb-1">স্পন্সর শিরোনাম</label><input name="sponsorTitle" value="${escapeHtml(cfg.sponsorTitle)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" /></div>
            <div><label class="text-[11px] font-bold block mb-1">সংক্ষিপ্ত বিবরণ</label><input name="sponsorSubtitle" value="${escapeHtml(cfg.sponsorSubtitle)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" /></div>
            <div><label class="text-[11px] font-bold block mb-1">ব্যানার ছবি URL (ঐচ্ছিক)</label><input name="sponsorImageUrl" value="${escapeHtml(cfg.sponsorImageUrl)}" placeholder="https://..." class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" /></div>
            <div class="grid grid-cols-2 gap-2">
              <div><label class="text-[11px] font-bold block mb-1">লিংক URL</label><input name="sponsorTargetUrl" value="${escapeHtml(cfg.sponsorTargetUrl)}" placeholder="https://..." class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" /></div>
              <div><label class="text-[11px] font-bold block mb-1">বাটন টেক্সট</label><input name="sponsorCtaText" value="${escapeHtml(cfg.sponsorCtaText)}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-xs" /></div>
            </div>
          </div>
        </div>

        <button type="submit" class="w-full py-3.5 rounded-2xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm shadow">
          💾 সব বিজ্ঞাপন সেটিংস সেভ করুন (অ্যাপ ও ওয়েবসাইটে লাইভ হবে)
        </button>
      </form>
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
    privacy: `<h2 class="text-xl font-bold text-[#105B34]">গোপনীয়তা নীতি (Privacy Policy)</h2><p class="text-sm text-gray-700 leading-relaxed">ব্যবহারকারীদের পাসওয়ার্ড SHA-256 হ্যাশ দিয়ে সুরক্ষিত রাখা হয় এবং প্রতিটি নোটিফিকেশন শুধুমাত্র নির্দিষ্ট প্রাপক বা অনুমোদিত ঘোষণার মধ্যেই সীমাবদ্ধ থাকে।</p><p class="text-sm text-gray-700 leading-relaxed mt-2">বিজ্ঞাপন ও মনিটাইজেশন: মোবাইল অ্যাপে Google AdMob এবং ওয়েবসাইটে Google AdSense, Adsterra, Monetag বা অনুমোদিত বিজ্ঞাপন নেটওয়ার্ক ব্যবহার হতে পারে। প্রাসঙ্গিক বিজ্ঞাপন দেখাতে কুকি বা Advertising ID ব্যবহৃত হতে পারে, তবে আপনার এনআইডি বা পাসওয়ার্ড কখনোই বিজ্ঞাপনদাতার সাথে শেয়ার করা হয় না।</p>`,
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
    state.filters = { query: "", categoryId: "", division: "", district: "", upazila: "", union: "", wholesaleOnly: "", sort: "NEWEST" };
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
  async handleActivationPaymentSubmit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    await submitActivationPayment({
      paymentMethod: fd.get("paymentMethod"),
      senderMobile: fd.get("senderMobile"),
      transactionId: fd.get("transactionId"),
      amount: fd.get("amount"),
      receiptUri: fd.get("receiptUri"),
      isRenewal: fd.get("isRenewal") === "1"
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
      isWholesale: fd.get("isWholesale") === "on",
      deliveryCharge: fd.get("deliveryCharge"),
      harvestDate: fd.get("harvestDate")
    });
  },
  addToCart,
  updateCartQty,
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
    preview.innerHTML = `<span>মোট ওজন: ${formatQty(kg)} কেজি</span><span>মোট বিক্রয় মূল্য: ${formatBdt(total)}</span>`;
  },
  updateFarmerPriceAndStock,
  updateOrderStatus,
  farmerConfirmPaymentReceived,
  adminVerifyOrderPayment,
  adminApproveActivationPayment,
  adminSetAccountStatus,
  setFarmerTab(t) { state.farmerTab = t; renderCurrentView(); },
  setAdminTab(t) { state.adminTab = t; renderCurrentView(); },
  setAdminUserFilter(f) { state.adminUserFilter = f; renderCurrentView(); },
  setAdminUserStatusFilter(s) { state.adminUserStatusFilter = s; renderCurrentView(); },
  setAdminUserSearch(q) { state.adminUserSearch = q; renderCurrentView(); },
  setAdminPaymentFilter(f) { state.adminPaymentFilter = f; renderCurrentView(); },
  setAdminPaymentQuery(q) { state.adminPaymentQuery = q; renderCurrentView(); },
  setAdminOrderFilter(f) { state.adminOrderFilter = f; renderCurrentView(); },
  setInfoTab(t) { state.infoTab = t; renderCurrentView(); },
  adminVerifyUser,
  adminVerifyFarmer: adminVerifyUser,
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
    const ps = state.paymentSettings || DEFAULT_PAYMENT_SETTINGS;
    openModal(`
      <h3 class="text-lg font-bold text-[#105B34] mb-2">💳 অর্ডার পেমেন্ট ও রসিদ জমা (${escapeHtml(order.orderCode)})</h3>
      <p class="text-xs text-gray-600 mb-3">মোট দেয়: <strong class="text-[#168447]">${formatBdt(order.grandTotal)}</strong> • কৃষক: ${escapeHtml(order.farmerName)} (${escapeHtml(order.farmerPhone)})</p>
      <div class="bg-[#EAF7EF] rounded-2xl p-3 text-[11px] text-[#105B34] mb-3">
        অফিশিয়াল নম্বর — bKash: ${escapeHtml(ps.bKashNumber)} (${escapeHtml(ps.bKashAccountType)}) • Nagad: ${escapeHtml(ps.nagadNumber)} (${escapeHtml(ps.nagadAccountType)})
      </div>
      <form onsubmit="window.kbApp.handleOrderPaymentSubmit(event, '${escapeHtml(order.id)}')" class="space-y-3">
        <div>
          <label class="text-xs font-bold block mb-1">পেমেন্ট মাধ্যম *</label>
          <select name="paymentMethod" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 bg-white text-sm">
            <option value="BKASH" ${order.paymentMethod === "BKASH" ? "selected" : ""}>বিকাশ (bKash)</option>
            <option value="NAGAD" ${order.paymentMethod === "NAGAD" ? "selected" : ""}>নগদ (Nagad)</option>
            <option value="BANK_TRANSFER" ${order.paymentMethod === "BANK_TRANSFER" ? "selected" : ""}>ব্যাংক ট্রান্সফার</option>
            <option value="CASH_ON_PICKUP" ${order.paymentMethod === "CASH_ON_PICKUP" ? "selected" : ""}>সরাসরি নগদ প্রদান</option>
          </select>
        </div>
        <div class="grid grid-cols-2 gap-2">
          <div>
            <label class="text-xs font-bold block mb-1">প্রেরক মোবাইল নম্বর</label>
            <input name="senderNumber" value="${escapeHtml(order.paymentSenderNumber || "")}" placeholder="017XXXXXXXX" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">Transaction ID</label>
            <input name="transactionReference" value="${escapeHtml(order.transactionReference || "")}" placeholder="TxID" class="w-full px-3 py-2 rounded-xl border border-emerald-200 text-sm uppercase" />
          </div>
        </div>
        <div class="space-y-2">
          <label class="text-xs font-bold block">পেমেন্ট রসিদ / স্ক্রিনশট (ঐচ্ছিক)</label>
          <label class="inline-flex cursor-pointer px-3 py-1.5 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-[#105B34] text-xs font-bold">
            📎 রসিদ আপলোড
            <input type="file" accept="image/*" onchange="window.kbApp.handleCompressedImageUpload(this, 'ord-pay-receipt-uri', 'ord-pay-receipt-preview')" class="hidden" />
          </label>
          <input type="hidden" name="receiptUri" id="ord-pay-receipt-uri" value="${escapeHtml(order.paymentReceiptUri || "")}" />
          <img id="ord-pay-receipt-preview" src="${escapeHtml(order.paymentReceiptUri || "")}" class="${order.paymentReceiptUri ? "" : "hidden"} max-h-36 rounded-xl border border-emerald-200 object-contain" alt="রসিদ" />
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
    await submitOrderPayment(orderId, {
      paymentMethod: fd.get("paymentMethod"),
      senderNumber: fd.get("senderNumber"),
      transactionReference: fd.get("transactionReference"),
      receiptUri: fd.get("receiptUri")
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
    await adminSaveAdConfig({
      adsEnabled: fd.get("adsEnabled") === "on",
      showAdsLabel: fd.get("showAdsLabel") === "on",
      androidAdsEnabled: fd.get("androidAdsEnabled") === "on",
      androidBannerId: String(fd.get("androidBannerId") || "").trim(),
      androidInterstitialId: String(fd.get("androidInterstitialId") || "").trim(),
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
