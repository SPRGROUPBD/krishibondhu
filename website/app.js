import {
  RTDB_CONFIG,
  SUPERADMIN_CREDENTIALS,
  DEFAULT_AD_CONFIG,
  MASTER_CATEGORIES,
  BD_DIVISIONS,
  BD_DISTRICTS,
  ORDER_STATUS_META,
  UNITS,
  BD_DISTRICT_CENTROIDS,
  buildInitialSeedLocations,
  resolveLocationFromCoords,
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
    wholesaleOnly: "",
    sort: "NEWEST"
  },
  farmerTab: "orders",
  adminTab: "overview",
  adminUserFilter: "ALL",
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
  try {
    const q = rtdbApi.query(
      rtdbApi.child(rtdbRef, "users"),
      rtdbApi.orderByChild("phone"),
      rtdbApi.equalTo(phone)
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

// ---------------------- Computed Helpers ----------------------

function getActiveUser() {
  if (!state.activeUserId) return null;
  return state.users.find((u) => u.id === state.activeUserId) || null;
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
  const isAdmin = user && user.role === "ADMIN";
  const unreadCount = getVisibleNotifications().filter((n) => !n.isRead).length;

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

  const adminDesktop = document.getElementById("nav-admin-btn");
  const adminMobile = document.getElementById("mobile-admin-btn");
  if (adminDesktop) adminDesktop.classList.toggle("hidden", !isAdmin);
  if (adminMobile) adminMobile.classList.toggle("hidden", !isAdmin);

  const authSlot = document.getElementById("auth-header-slot");
  if (authSlot) {
    if (user) {
      const roleLabel = user.role === "ADMIN" ? "অ্যাডমিন" : user.role === "FARMER" ? "কৃষক" : "ক্রেতা";
      authSlot.innerHTML = `
        <div class="flex items-center gap-1.5">
          <button onclick="window.kbApp.navigate('${user.role === "ADMIN" ? "admin" : user.role === "FARMER" ? "farmer-dashboard" : "orders"}')"
            class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold border border-emerald-200 hover:bg-emerald-100 max-w-[140px] truncate">
            ${escapeHtml(user.fullName)} (${roleLabel})
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
          <button onclick="window.kbApp.navigate('auth-register-farmer')" class="hidden sm:inline-flex px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-semibold hover:bg-[#105B34]">
            ${t("নিবন্ধন", "Register")}
          </button>
        </div>
      `;
    }
  }

  document.querySelectorAll(".nav-btn").forEach((btn) => {
    const active = btn.dataset.nav === state.page;
    btn.classList.toggle("bg-[#EAF7EF]", active);
    btn.classList.toggle("text-[#105B34]", active);
    btn.classList.toggle("font-bold", active);
  });
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

  const expectedHash = await hashPassword(rawPassword);
  let user = state.users.find((u) => u.phone === cleanId);
  if (!user) {
    const remote = await rtdbFetchUserByPhone(cleanId);
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
  saveLocalSnapshot();
  showToast(`স্বাগতম, ${user.fullName}!`, "success");
  navigate(user.role === "FARMER" ? "farmer-dashboard" : "home");
  return true;
}

async function registerUser(form) {
  const fullName = String(form.fullName || "").trim();
  const phone = String(form.phone || "").trim();
  const rawPassword = String(form.password || "");
  const role = form.role === "FARMER" ? "FARMER" : "BUYER";

  if (!fullName || phone.length < 10 || rawPassword.length < 6) {
    showToast("নাম, সঠিক মোবাইল নম্বর ও কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড দিন।", "error");
    return false;
  }
  if (phone.toLowerCase() === SUPERADMIN_CREDENTIALS.id) {
    showToast("এই আইডি ব্যবহার করা যাবে না।", "error");
    return false;
  }
  if (state.users.some((u) => u.phone === phone) || (await rtdbFetchUserByPhone(phone))) {
    showToast("এই মোবাইল নম্বরটি ইতোমধ্যে নিবন্ধিত।", "error");
    return false;
  }

  const resolvedDistrict = form.district || "ঢাকা";
  const districtObj = BD_DISTRICTS.find((d) => d.districtBn === resolvedDistrict);
  const resolvedDivision = form.division || districtObj?.divisionBn || "ঢাকা";
  const resolvedUpazila = form.upazila || districtObj?.upazilas?.[0] || resolvedDistrict;

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
    nidMasked: nidLast4 ? `NID-****-${nidLast4.slice(-4)}` : "",
    verificationStatus: "PENDING",
    isMobileVerified: true,
    rating: 5.0,
    reviewCount: 0,
    createdAt: Date.now()
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
    detailsBn: `${user.fullName} — ${user.upazila}, ${user.district}, ${user.division}`,
    createdAt: Date.now()
  };
  upsertArrayItem(state.auditLogs, audit);
  saveLocalSnapshot();

  await rtdbPush(`users/${uid}`, user);
  await rtdbPush(`auditLogs/${audit.id}`, audit);

  showToast(`নিবন্ধন সফল হয়েছে! স্বাগতম, ${user.fullName} (অ্যাডমিন যাচাই অপেক্ষমাণ)`, "success");
  navigate(role === "FARMER" ? "farmer-dashboard" : "home");
  return true;
}

function logout() {
  state.activeUserId = "";
  saveLocalSnapshot();
  showToast("সফলভাবে লগআউট সম্পন্ন হয়েছে।", "info");
  navigate("home");
}

function addToCart(productId, qty) {
  const product = state.products.find((p) => p.id === productId);
  const reqQty = Number(qty || 1);
  if (!product || reqQty <= 0) {
    showToast("সঠিক পরিমাণ নির্বাচন করুন।", "error");
    return;
  }
  if (product.status !== "ACTIVE" || product.availableQuantity <= 0) {
    showToast("দুঃখিত, এই ফসলটির মজুত শেষ।", "error");
    return;
  }
  const existing = state.cart.find((c) => c.productId === productId);
  const newTotal = (existing ? existing.quantity : 0) + reqQty;
  if (newTotal > product.availableQuantity) {
    showToast(`সর্বোচ্চ মজুত ${formatQty(product.availableQuantity)} ${product.unit} পর্যন্ত যোগ করা যাবে।`, "error");
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
      unit: product.unit,
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
      showToast(`পর্যাপ্ত মজুত নেই (সর্বোচ্চ ${formatQty(maxAvail)})`, "error");
      return;
    }
    item.quantity = q;
  }
  saveLocalSnapshot();
  renderCurrentView();
}

async function placeOrdersFromCart({ deliveryOption, deliveryAddress, paymentMethod, transactionRef }) {
  const buyer = getActiveUser();
  if (!buyer) {
    showToast("অর্ডার করতে অনুগ্রহ করে লগইন বা নিবন্ধন করুন।", "error");
    navigate("auth-login");
    return;
  }
  if (!state.cart.length) {
    showToast("আপনার কার্ট খালি।", "error");
    return;
  }
  if (deliveryOption === "DIRECT_DELIVERY" && !String(deliveryAddress || "").trim()) {
    showToast("ডেলিভারির ঠিকানা লিখুন।", "error");
    return;
  }
  const isDigital = paymentMethod !== "CASH_ON_PICKUP" && paymentMethod !== "CASH_ON_DELIVERY";
  if (isDigital && !String(transactionRef || "").trim()) {
    showToast("ডিজিটাল পেমেন্টের জন্য ট্রানজেকশন রেফারেন্স দিন।", "error");
    return;
  }

  // Validate stock across all cart items first
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
        unit: ci.unit,
        quantity: ci.quantity,
        pricePerUnit: ci.pricePerUnit,
        lineTotal
      };
    }

    const deliveryCharge = deliveryOption === "FARM_PICKUP" ? 0 : maxDeliveryFee;
    const grandTotal = productTotal + deliveryCharge;
    const itemsSummary = farmerItems.map((i) => `${i.productNameBn} × ${formatQty(i.quantity)} ${i.unit}`).join(", ");

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
      paymentMethod,
      transactionReference: String(transactionRef || "").trim(),
      paymentStatus: isDigital ? "AWAITING_GATEWAY" : "PENDING",
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
  showToast(`${createdOrders.length}টি অর্ডার সম্পন্ন হয়েছে ও কৃষককে নোটিফিকেশন পাঠানো হয়েছে!`, "success");
  navigate("orders");
}

async function createFarmerProduct(form) {
  const farmer = getActiveUser();
  if (!farmer || farmer.role !== "FARMER") {
    showToast("শুধুমাত্র নিবন্ধিত কৃষক ফসল যুক্ত করতে পারেন।", "error");
    return false;
  }
  const qty = Number(form.quantity);
  const price = Number(form.pricePerUnit);
  const fee = Number(form.deliveryCharge || 0);
  if (!form.nameBn.trim() || qty <= 0 || price <= 0 || fee < 0) {
    showToast("ফসলের নাম, সঠিক পরিমাণ ও দাম দিন।", "error");
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
    nameBn: form.nameBn.trim(),
    nameEn: form.nameBn.trim(),
    descriptionBn: String(form.descriptionBn || "").trim(),
    imageUri: String(form.imageUri || "").trim(),
    availableQuantity: qty,
    unit: form.unit || "কেজি",
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
    detailsBn: `${product.nameBn} (${product.district}) — ${formatBdt(product.pricePerUnit)}/${product.unit}`,
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
  const farmer = getActiveUser();
  const prod = state.products.find((p) => p.id === productId);
  if (!farmer || !prod || prod.farmerId !== farmer.id) {
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
  prod.status = s > 0 ? "ACTIVE" : "OUT_OF_STOCK";
  prod.updatedAt = Date.now();
  saveLocalSnapshot();
  await rtdbPush(`products/${prod.id}`, prod);
  showToast("দাম ও মজুত আপডেট হয়েছে!", "success");
  renderCurrentView();
}

async function updateOrderStatus(orderId, newStatus) {
  const actor = getActiveUser();
  const order = state.orders.find((o) => o.id === orderId);
  if (!actor || !order) return;

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
  if (newStatus === "COMPLETED" && order.paymentStatus === "PENDING") {
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
      (p.categoryNameBn || "").toLowerCase().includes(q);
    const matchCat = !f.categoryId || p.categoryId === f.categoryId;
    const matchDiv = !f.division || p.division === f.division;
    const matchDist = !f.district || p.district === f.district;
    const matchUpz = !f.upazila || p.upazila === f.upazila;
    const matchW = f.wholesaleOnly === "" || String(Boolean(p.isWholesale)) === f.wholesaleOnly;
    return matchQ && matchCat && matchDiv && matchDist && matchUpz && matchW;
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
      (u.farmBio || "").toLowerCase().includes(q);
    const matchDiv = !f.division || u.division === f.division;
    const matchDist = !f.district || u.district === f.district;
    const matchUpz = !f.upazila || u.upazila === f.upazila;
    return matchQ && matchDiv && matchDist && matchUpz;
  });
}

function renderProductCard(p) {
  const cat = state.categories.find((c) => c.id === p.categoryId);
  const emoji = cat?.emoji || "🌾";
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
          <p class="text-xs text-gray-600 mt-0.5">👨‍🌾 ${escapeHtml(p.farmerName)} • 📍 ${escapeHtml(p.upazila || "")}, ${escapeHtml(p.district || "")}</p>
          <div class="mt-2 flex items-baseline justify-between">
            <span class="text-lg font-extrabold text-[#168447]">${formatBdt(p.pricePerUnit)}<span class="text-xs font-medium text-gray-500"> / ${escapeHtml(p.unit)}</span></span>
            <span class="text-xs font-medium text-emerald-700 bg-[#EAF7EF] px-2 py-0.5 rounded-full">মজুত: ${formatQty(p.availableQuantity)} ${escapeHtml(p.unit)}</span>
          </div>
        </div>
        <div class="flex gap-2">
          <button onclick="window.kbApp.addToCart('${escapeHtml(p.id)}', 1)" class="flex-1 py-2.5 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white text-xs font-bold transition">
            + কার্টে যোগ করুন
          </button>
          <button onclick="window.kbApp.navigate('product-detail', { id: '${escapeHtml(p.id)}' })" class="px-3 py-2.5 rounded-xl border border-emerald-200 text-xs font-semibold text-[#105B34] hover:bg-[#EAF7EF]">
            বিস্তারিত
          </button>
        </div>
      </div>
    </div>
  `;
}

// ---------------------- Page Views ----------------------

function renderHomePage() {
  const activeProducts = state.products.filter((p) => p.status === "ACTIVE");
  const farmers = state.users.filter((u) => u.role === "FARMER");
  const topCats = state.categories.slice(0, 12);

  return `
    <div class="space-y-8">
      <!-- Hero Banner -->
      <section class="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#105B34] via-[#168447] to-emerald-600 text-white shadow-lg">
        <div class="grid md:grid-cols-2 gap-6 items-center p-6 sm:p-10">
          <div class="space-y-4">
            <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/15 backdrop-blur text-xs font-semibold">
              <span>🇧🇩 কুষ্টিয়া থেকে সারা বাংলাদেশ (৬৪ জেলা)</span>
              <span class="px-2 py-0.5 rounded-full bg-amber-400 text-[#105B34] font-bold">০% দালাল</span>
            </div>
            <h1 class="text-2xl sm:text-4xl font-extrabold leading-tight">
              কৃষকের ফসল, কৃষকের ন্যায্য দাম — সরাসরি ক্রেতার হাতে।
            </h1>
            <p class="text-sm sm:text-base text-emerald-50/90 leading-relaxed">
              কোনো মধ্যস্বত্বভোগী বা কমিশন ছাড়াই সরাসরি বাংলাদেশের কৃষকদের কাছ থেকে টাটকা ধান, চাল, আলু, পেঁয়াজ, ফল ও শাকসবজি কিনুন। অর্ডার করলে তাৎক্ষণিক রিয়েল-টাইম নোটিফিকেশন যায় কৃষকের কাছে।
            </p>
            <div class="flex flex-wrap gap-3 pt-1">
              <button onclick="window.kbApp.navigate('browse')" class="px-5 py-3 rounded-2xl bg-white text-[#105B34] font-bold text-sm shadow hover:bg-emerald-50">
                🌾 ফসল খুঁজুন
              </button>
              <button onclick="window.kbApp.goSell()" class="px-5 py-3 rounded-2xl bg-amber-400 text-[#105B34] font-bold text-sm shadow hover:bg-amber-300">
                👨‍🌾 কৃষক হিসেবে বিক্রি করুন
              </button>
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
                <p class="text-sm text-gray-600 max-w-md mx-auto">কোনো ডেমো ডেটা রাখা হয়নি। কৃষক হিসেবে নিবন্ধন করে প্রথম তাজা ফসল যোগ করুন!</p>
                <button onclick="window.kbApp.goSell()" class="px-5 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">ফসল যোগ করুন</button>
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

function renderBrowsePage() {
  const f = state.filters;
  const distOptions = f.division ? BD_DISTRICTS.filter((d) => d.divisionBn === f.division) : BD_DISTRICTS;
  const upzOptions = f.district ? BD_DISTRICTS.find((d) => d.districtBn === f.district)?.upazilas || [] : [];
  const filtered = getFilteredProducts();

  return `
    <div class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <h1 class="text-2xl font-bold text-[#105B34]">🌾 সারা বাংলাদেশের ফসল খুঁজুন</h1>
          <p class="text-xs text-gray-600">মোট ${filtered.length}টি সক্রিয় ফসল পাওয়া গেছে</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button onclick="window.kbApp.detectBrowserLocation('browse')" class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-xs font-bold text-[#105B34] hover:bg-emerald-100">
            📍 আমার এলাকার ফসল (GPS)
          </button>
          <button onclick="window.kbApp.openLocationMapModal('browse')" class="px-3 py-1.5 rounded-xl bg-white border border-emerald-200 text-xs font-bold text-[#105B34] hover:bg-[#EAF7EF]">
            🗺️ ম্যাপ থেকে জেলা
          </button>
          <button onclick="window.kbApp.resetFilters()" class="px-3 py-1.5 rounded-xl border border-emerald-200 text-xs font-semibold text-[#105B34] hover:bg-[#EAF7EF]">
            ফিল্টার রিসেট
          </button>
        </div>
      </div>

      <!-- Cascading Dropdown Filters -->
      <div class="bg-white rounded-3xl p-4 sm:p-5 border border-emerald-100 shadow-sm grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-7 gap-3">
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
              <p class="text-xs text-gray-500 mt-1">অন্য জেলা বা ক্যাটাগরি নির্বাচন করে চেষ্টা করুন।</p>
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
  const farmer = state.users.find((u) => u.id === prod.farmerId);
  const maxQ = Math.max(0.5, Number(prod.availableQuantity || 0));
  const initialQ = Math.min(1, maxQ);

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
            ${prod.isWholesale ? `<span class="px-3 py-1 rounded-full bg-amber-100 text-amber-800 text-xs font-bold">পাইকারি</span>` : ""}
            ${prod.farmerVerified ? `<span class="px-3 py-1 rounded-full bg-emerald-600 text-white text-xs font-bold">✓ যাচাইকৃত কৃষক</span>` : ""}
          </div>
          <h1 class="text-2xl sm:text-3xl font-extrabold text-[#202820]">${escapeHtml(prod.nameBn)}</h1>
          <div class="text-2xl font-extrabold text-[#168447]">${formatBdt(prod.pricePerUnit)} <span class="text-sm font-medium text-gray-500">/ ${escapeHtml(prod.unit)}</span></div>
          <p class="text-sm text-gray-700 leading-relaxed">${escapeHtml(prod.descriptionBn || "সরাসরি কৃষকের খামার থেকে সংগ্রহ করা তাজা কৃষিপণ্য।")}</p>

          <div class="grid grid-cols-2 gap-3 bg-[#F7FAF7] p-4 rounded-2xl text-xs">
            <div><span class="text-gray-500 block">উপলব্ধ মজুত</span><strong class="text-sm">${formatQty(prod.availableQuantity)} ${escapeHtml(prod.unit)}</strong></div>
            <div><span class="text-gray-500 block">এলাকা</span><strong class="text-sm">${escapeHtml(prod.upazila)}, ${escapeHtml(prod.district)}</strong></div>
            <div><span class="text-gray-500 block">ফসল তোলার তারিখ</span><strong>${escapeHtml(prod.harvestDate || "আজ")}</strong></div>
            <div><span class="text-gray-500 block">সংগ্রহ ও ডেলিভারি</span><strong>${prod.supportsDirectDelivery ? `হোম ডেলিভারি (${formatBdt(prod.deliveryChargeEstimate)})` : "খামার থেকে সংগ্রহ"}</strong></div>
          </div>

          <div class="flex items-center gap-3 pt-2">
            <input id="detail-qty" type="number" min="0.5" max="${maxQ}" step="0.5" value="${initialQ}"
              class="w-28 px-3 py-3 rounded-xl border border-emerald-200 text-center font-bold" />
            <button onclick="window.kbApp.addToCart('${escapeHtml(prod.id)}', document.getElementById('detail-qty').value)"
              class="flex-1 py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">
              🛒 কার্টে যোগ করুন
            </button>
          </div>

          <!-- Farmer card -->
          <div class="border-t border-emerald-100 pt-4 flex items-center justify-between gap-3">
            <div>
              <div class="font-bold text-sm text-[#105B34]">👨‍🌾 ${escapeHtml(prod.farmerName)}</div>
              <div class="text-xs text-gray-600">${escapeHtml(prod.pickupAddress || prod.district)} • ★ ${(farmer?.rating || 5).toFixed(1)} (${farmer?.reviewCount || 0} রিভিউ)</div>
            </div>
            <div class="flex gap-2">
              ${farmer?.phone ? `<a href="tel:${escapeHtml(farmer.phone)}" class="px-3 py-2 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold">📞 কল</a>` : ""}
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
  const distOptions = f.division ? BD_DISTRICTS.filter((d) => d.divisionBn === f.division) : BD_DISTRICTS;
  const upzOptions = f.district ? BD_DISTRICTS.find((d) => d.districtBn === f.district)?.upazilas || [] : [];
  const farmers = getFilteredFarmers();

  return `
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 class="text-2xl font-bold text-[#105B34]">👨‍🌾 বাংলাদেশের কৃষকদের খুঁজুন</h1>
          <p class="text-xs text-gray-600">সরাসরি কৃষকের সাথে কথা বলে ফসল কিনুন — কোনো দালাল নেই</p>
        </div>
        <div class="flex gap-2">
          <button onclick="window.kbApp.detectBrowserLocation('farmers')" class="px-3 py-1.5 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-xs font-bold text-[#105B34]">📍 GPS অটো</button>
          <button onclick="window.kbApp.openLocationMapModal('farmers')" class="px-3 py-1.5 rounded-xl bg-white border border-emerald-200 text-xs font-bold text-[#105B34]">🗺️ ম্যাপ</button>
        </div>
      </div>

      <div class="bg-white rounded-3xl p-4 border border-emerald-100 shadow-sm grid sm:grid-cols-2 lg:grid-cols-4 gap-3">
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
                        <div>
                          <h3 class="font-bold text-base text-[#105B34]">${escapeHtml(u.fullName)}</h3>
                          <p class="text-xs text-gray-600">📍 ${escapeHtml(u.upazila)}, ${escapeHtml(u.district)} (${escapeHtml(u.division)})</p>
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
                        <a href="tel:${escapeHtml(u.phone)}" class="px-3 py-2 rounded-xl border border-emerald-200 text-xs font-bold text-[#105B34]">📞 কল</a>
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

  return `
    <div class="space-y-6">
      <button onclick="window.kbApp.navigate('farmers')" class="text-sm font-semibold text-[#168447] hover:underline">← কৃষক তালিকায় ফিরুন</button>
      <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div class="space-y-1">
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold text-[#105B34]">${escapeHtml(farmer.fullName)}</h1>
            ${farmer.verificationStatus === "VERIFIED" ? `<span class="px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 text-xs font-bold">✓ যাচাইকৃত কৃষক</span>` : ""}
          </div>
          <p class="text-sm text-gray-600">📍 ${escapeHtml(farmer.village ? farmer.village + ", " : "")}${escapeHtml(farmer.upazila)}, ${escapeHtml(farmer.district)}, ${escapeHtml(farmer.division)}</p>
          <p class="text-sm text-gray-700">${escapeHtml(farmer.farmBio || "")}</p>
          <p class="text-xs font-bold text-amber-700">★ ${Number(farmer.rating || 5).toFixed(1)} (${farmer.reviewCount || 0} রিভিউ)</p>
        </div>
        <div class="flex gap-2">
          <a href="tel:${escapeHtml(farmer.phone)}" class="px-4 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">📞 কল: ${escapeHtml(farmer.phone)}</a>
          <a href="sms:${escapeHtml(farmer.phone)}" class="px-4 py-2.5 rounded-xl border border-emerald-200 text-[#105B34] text-sm font-bold">💬 SMS</a>
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

        <div>
          <label class="text-xs font-bold text-gray-700 block mb-1">পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর) *</label>
          <input name="password" type="password" minlength="6" required class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
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
            <label class="text-xs font-bold text-gray-700 block mb-1">এনআইডি শেষ ৪ সংখ্যা (অ্যাডমিন যাচাইয়ের জন্য ঐচ্ছিক)</label>
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
          ${isFarmer ? "OTP ছাড়াই কৃষক হিসেবে নিবন্ধন করুন" : "OTP ছাড়াই ক্রেতা হিসেবে নিবন্ধন করুন"}
        </button>
      </form>
      <p class="text-center text-xs text-gray-600">ইতোমধ্যে অ্যাকাউন্ট আছে? <button onclick="window.kbApp.navigate('auth-login')" class="text-[#168447] font-bold hover:underline">লগইন করুন</button></p>
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

  return `
    <div class="space-y-6">
      <!-- Farmer Header -->
      <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-2xl font-bold text-[#105B34]">👨‍🌾 কৃষক ড্যাশবোর্ড</h1>
            <span class="px-2.5 py-0.5 rounded-full text-xs font-bold ${user.verificationStatus === "VERIFIED" ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-800"}">
              ${user.verificationStatus === "VERIFIED" ? "✓ যাচাইকৃত" : "যাচাই অপেক্ষমাণ"}
            </span>
          </div>
          <p class="text-xs text-gray-600">${escapeHtml(user.fullName)} • 📍 ${escapeHtml(user.upazila)}, ${escapeHtml(user.district)}, ${escapeHtml(user.division)}</p>
        </div>
        <div class="flex gap-3 text-center">
          <div class="px-4 py-2 rounded-2xl bg-[#EAF7EF]"><div class="font-extrabold text-[#105B34]">${myProducts.length}</div><div class="text-[11px]">আমার ফসল</div></div>
          <div class="px-4 py-2 rounded-2xl bg-[#EAF7EF]"><div class="font-extrabold text-[#105B34]">${myOrders.length}</div><div class="text-[11px]">মোট অর্ডার</div></div>
          <div class="px-4 py-2 rounded-2xl bg-[#EAF7EF]"><div class="font-extrabold text-[#105B34]">${formatBdt(totalSales)}</div><div class="text-[11px]">মোট বিক্রয়</div></div>
        </div>
      </div>

      <!-- Tabs -->
      <div class="flex flex-wrap gap-2">
        <button onclick="window.kbApp.setFarmerTab('orders')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "orders" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">📦 প্রাপ্ত অর্ডার (${myOrders.length})</button>
        <button onclick="window.kbApp.setFarmerTab('products')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "products" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">🌾 আমার ফসল (${myProducts.length})</button>
        <button onclick="window.kbApp.setFarmerTab('add')" class="px-4 py-2 rounded-xl text-sm font-bold ${tab === "add" ? "bg-[#168447] text-white" : "bg-white border border-emerald-200 text-[#105B34]"}">+ নতুন ফসল যোগ করুন</button>
      </div>

      ${tab === "orders" ? renderFarmerOrdersTab(myOrders) : tab === "products" ? renderFarmerProductsTab(myProducts) : renderAddProductTab(user)}
    </div>
  `;
}

function renderFarmerOrdersTab(myOrders) {
  if (!myOrders.length) {
    return `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200">এখনো কোনো অর্ডার আসেনি। ক্রেতা অর্ডার করলেই এখানে ও নোটিফিকেশনে সাথে সাথে দেখাবে।</div>`;
  }
  return `
    <div class="space-y-4">
      ${myOrders
        .map((o) => {
          const meta = ORDER_STATUS_META[o.status] || ORDER_STATUS_META.PENDING;
          return `
            <div class="bg-white rounded-3xl p-5 border border-emerald-100 shadow-sm space-y-3">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <span class="font-extrabold text-[#105B34]">${escapeHtml(o.orderCode)}</span>
                  <span class="text-xs text-gray-500 ml-2">${new Date(o.createdAt).toLocaleString("bn-BD")}</span>
                </div>
                <span class="px-3 py-1 rounded-full text-xs font-bold ${meta.color}">${meta.labelBn}</span>
              </div>
              <div class="text-sm font-medium">${escapeHtml(o.itemsSummary)}</div>
              <div class="text-xs text-gray-600 grid sm:grid-cols-2 gap-1">
                <div>ক্রেতা: <strong>${escapeHtml(o.buyerName)}</strong> (<a class="text-[#168447] underline" href="tel:${escapeHtml(o.buyerPhone)}">${escapeHtml(o.buyerPhone)}</a>)</div>
                <div>মোট মূল্য: <strong class="text-[#168447]">${formatBdt(o.grandTotal)}</strong> (${escapeHtml(o.paymentMethod)})</div>
                <div class="sm:col-span-2">ঠিকানা: ${escapeHtml(o.deliveryAddress)}</div>
              </div>
              <div class="flex flex-wrap gap-2 pt-1">
                ${o.status === "PENDING" ? `
                  <button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'ACCEPTED')" class="px-3.5 py-2 rounded-xl bg-[#168447] text-white text-xs font-bold">✓ গ্রহণ করুন</button>
                  <button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'REJECTED')" class="px-3.5 py-2 rounded-xl bg-red-600 text-white text-xs font-bold">✕ প্রত্যাখ্যান করুন</button>
                ` : ""}
                ${o.status === "ACCEPTED" ? `<button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'PROCESSING')" class="px-3.5 py-2 rounded-xl bg-sky-600 text-white text-xs font-bold">প্রস্তুত হচ্ছে</button>` : ""}
                ${o.status === "PROCESSING" ? `<button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'READY')" class="px-3.5 py-2 rounded-xl bg-teal-600 text-white text-xs font-bold">সংগ্রহের জন্য প্রস্তুত</button>` : ""}
                ${o.status === "READY" ? `<button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'SHIPPED')" class="px-3.5 py-2 rounded-xl bg-indigo-600 text-white text-xs font-bold">পথে পাঠানো হয়েছে</button>` : ""}
                ${["READY", "SHIPPED"].includes(o.status) ? `<button onclick="window.kbApp.updateOrderStatus('${escapeHtml(o.id)}', 'COMPLETED')" class="px-3.5 py-2 rounded-xl bg-emerald-700 text-white text-xs font-bold">✓ সম্পন্ন হয়েছে</button>` : ""}
              </div>
            </div>
          `;
        })
        .join("")}
    </div>
  `;
}

function renderFarmerProductsTab(myProducts) {
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
                <p class="text-xs text-gray-500">${escapeHtml(p.categoryNameBn)} • ${escapeHtml(p.district)}</p>
              </div>
              <span class="px-2 py-0.5 rounded-full text-[11px] font-bold ${p.status === "ACTIVE" ? "bg-emerald-100 text-emerald-800" : "bg-gray-200 text-gray-700"}">${escapeHtml(p.status)}</span>
            </div>
            <div class="grid grid-cols-2 gap-2 text-xs">
              <div>
                <label class="text-gray-500 block mb-1">দাম (৳/${escapeHtml(p.unit)})</label>
                <input id="price_${escapeHtml(p.id)}" type="number" min="1" step="0.5" value="${p.pricePerUnit}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 font-bold" />
              </div>
              <div>
                <label class="text-gray-500 block mb-1">মজুত (${escapeHtml(p.unit)})</label>
                <input id="stock_${escapeHtml(p.id)}" type="number" min="0" step="0.5" value="${p.availableQuantity}" class="w-full px-3 py-2 rounded-xl border border-emerald-200 font-bold" />
              </div>
            </div>
            <button onclick="window.kbApp.updateFarmerPriceAndStock('${escapeHtml(p.id)}', document.getElementById('price_${escapeHtml(p.id)}').value, document.getElementById('stock_${escapeHtml(p.id)}').value)"
              class="w-full py-2.5 rounded-xl bg-[#168447] text-white text-xs font-bold hover:bg-[#105B34]">
              আপডেট সংরক্ষণ করুন
            </button>
          </div>
        `
        )
        .join("")}
    </div>
  `;
}

function renderAddProductTab(user) {
  return `
    <div class="bg-white rounded-3xl p-6 sm:p-8 border border-emerald-100 shadow-sm max-w-2xl">
      <h2 class="text-lg font-bold text-[#105B34] mb-4">নতুন ফসল যুক্ত করুন (০% কমিশন)</h2>
      <form onsubmit="window.kbApp.handleCreateProductSubmit(event)" class="space-y-4">
        <div class="grid sm:grid-cols-2 gap-3">
          <div>
            <label class="text-xs font-bold block mb-1">ফসলের নাম *</label>
            <input name="nameBn" required placeholder="যেমন: দেশি মিনিকেট চাল" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">ক্যাটাগরি *</label>
            <select name="categoryId" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
              ${state.categories.map((c) => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.emoji || "")} ${escapeHtml(c.nameBn)}</option>`).join("")}
            </select>
          </div>
        </div>

        <div class="grid grid-cols-3 gap-3">
          <div>
            <label class="text-xs font-bold block mb-1">পরিমাণ *</label>
            <input name="quantity" type="number" min="0.5" step="0.5" required placeholder="50" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">একক</label>
            <select name="unit" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 text-sm bg-white">
              ${UNITS.map((u) => `<option value="${escapeHtml(u)}">${escapeHtml(u)}</option>`).join("")}
            </select>
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">প্রতি একক দাম (৳) *</label>
            <input name="pricePerUnit" type="number" min="1" step="0.5" required placeholder="70" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
        </div>

        <div>
          <label class="text-xs font-bold block mb-1">বিস্তারিত বিবরণ</label>
          <textarea name="descriptionBn" rows="2" placeholder="ফসলের মান, জাত এবং তাজা অবস্থা সম্পর্কে লিখুন..." class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm"></textarea>
        </div>

        <div class="grid sm:grid-cols-2 gap-3">
          <div class="space-y-2">
            <label class="text-xs font-bold block">ফসলের ছবি (ক্যামেরা / ফাইল ও গ্যালারি)</label>
            <div class="flex flex-wrap gap-2">
              <label class="cursor-pointer px-3 py-2 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-[#105B34] text-xs font-bold hover:bg-emerald-100">
                📷 ক্যামেরা
                <input type="file" accept="image/*" capture="environment" onchange="window.kbApp.handleProductImageUpload(this)" class="hidden" />
              </label>
              <label class="cursor-pointer px-3 py-2 rounded-xl bg-white border border-emerald-200 text-[#105B34] text-xs font-bold hover:bg-[#EAF7EF]">
                🖼️ গ্যালারি / ফাইল
                <input type="file" accept="image/*" onchange="window.kbApp.handleProductImageUpload(this)" class="hidden" />
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
            <input id="new-prod-pickup-address" name="pickupAddress" value="${escapeHtml(`${user.upazila}, ${user.district}`)}" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
        </div>

        <div class="grid sm:grid-cols-3 gap-3 items-center bg-[#F7FAF7] p-3.5 rounded-2xl text-xs font-semibold">
          <label class="flex items-center gap-2"><input type="checkbox" name="supportsFarmPickup" checked /> খামার থেকে সংগ্রহ</label>
          <label class="flex items-center gap-2"><input type="checkbox" name="supportsDirectDelivery" checked /> সরাসরি ডেলিভারি</label>
          <label class="flex items-center gap-2"><input type="checkbox" name="isWholesale" /> পাইকারি বিক্রি</label>
        </div>

        <div class="grid sm:grid-cols-2 gap-3">
          <div>
            <label class="text-xs font-bold block mb-1">ডেলিভারি চার্জ (৳)</label>
            <input name="deliveryCharge" type="number" min="0" value="60" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">ফসল তোলার তারিখ</label>
            <input name="harvestDate" placeholder="আজই সংগ্রহ" class="w-full px-3.5 py-2.5 rounded-xl border border-emerald-200 text-sm" />
          </div>
        </div>

        <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold text-sm">
          ফসল তালিকাভুক্ত করুন
        </button>
      </form>
    </div>
  `;
}

function renderCartPage() {
  if (!state.cart.length) {
    return `
      <div class="bg-white rounded-3xl p-12 text-center border border-emerald-100 max-w-lg mx-auto space-y-3">
        <div class="text-5xl">🛒</div>
        <h1 class="text-xl font-bold text-[#105B34]">আপনার কার্ট খালি</h1>
        <p class="text-sm text-gray-600">তাজা কৃষিপণ্য কার্টে যোগ করে সরাসরি কৃষকের কাছ থেকে অর্ডার করুন।</p>
        <button onclick="window.kbApp.navigate('browse')" class="px-5 py-2.5 rounded-xl bg-[#168447] text-white text-sm font-bold">ফসল খুঁজুন</button>
      </div>
    `;
  }

  const productTotal = state.cart.reduce((s, c) => s + c.pricePerUnit * c.quantity, 0);
  const user = getActiveUser();

  return `
    <div class="grid lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2 space-y-3">
        <h1 class="text-2xl font-bold text-[#105B34]">🛒 আপনার শপিং কার্ট (${state.cart.length}টি আইটেম)</h1>
        ${state.cart
          .map(
            (c) => `
            <div class="bg-white rounded-2xl p-4 border border-emerald-100 flex items-center justify-between gap-3">
              <div>
                <h3 class="font-bold text-sm text-[#202820]">${escapeHtml(c.productNameBn)}</h3>
                <p class="text-xs text-gray-500">কৃষক: ${escapeHtml(c.farmerName)} • ${formatBdt(c.pricePerUnit)} / ${escapeHtml(c.unit)}</p>
              </div>
              <div class="flex items-center gap-2">
                <button onclick="window.kbApp.updateCartQty('${escapeHtml(c.id)}', ${c.quantity - 1})" class="w-8 h-8 rounded-lg bg-[#EAF7EF] font-bold text-[#105B34]">-</button>
                <span class="text-sm font-bold w-12 text-center">${formatQty(c.quantity)}</span>
                <button onclick="window.kbApp.updateCartQty('${escapeHtml(c.id)}', ${c.quantity + 1})" class="w-8 h-8 rounded-lg bg-[#EAF7EF] font-bold text-[#105B34]">+</button>
                <span class="font-bold text-sm text-[#168447] ml-2 w-20 text-right">${formatBdt(c.pricePerUnit * c.quantity)}</span>
              </div>
            </div>
          `
          )
          .join("")}
      </div>

      <div class="bg-white rounded-3xl p-6 border border-emerald-100 shadow-sm h-fit space-y-4">
        <h2 class="text-lg font-bold text-[#105B34]">চেকআউট ও অর্ডার সম্পন্ন</h2>
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
            <input id="checkout-delivery-address" name="deliveryAddress" value="${escapeHtml(user ? `${user.village ? user.village + ", " : ""}${user.upazila}, ${user.district}` : "")}" placeholder="বাড়ি, গ্রাম, উপজেলা, জেলা" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200" />
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">পেমেন্ট মাধ্যম</label>
            <select name="paymentMethod" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200 bg-white">
              <option value="CASH_ON_PICKUP">নগদ (খামারে সংগ্রহ / ক্যাশ অন ডেলিভারি)</option>
              <option value="BKASH">বিকাশ (bKash)</option>
              <option value="NAGAD">নগদ (Nagad)</option>
              <option value="BANK_TRANSFER">ব্যাংক ট্রান্সফার</option>
            </select>
          </div>
          <div>
            <label class="text-xs font-bold block mb-1">ট্রানজেকশন রেফারেন্স (ডিজিটাল পেমেন্টে)</label>
            <input name="transactionRef" placeholder="TrxID (ঐচ্ছিক)" class="w-full px-3 py-2.5 rounded-xl border border-emerald-200" />
          </div>
          <div class="border-t border-emerald-100 pt-3 flex justify-between font-bold text-base">
            <span>মোট পণ্যের দাম</span>
            <span class="text-[#168447]">${formatBdt(productTotal)}</span>
          </div>
          <p class="text-[11px] text-gray-500">অর্ডার কনফার্ম করলেই তাৎক্ষণিক কৃষকের কাছে অর্ডার নোটিফিকেশন পাঠানো হবে। ০% প্ল্যাটফর্ম কমিশন।</p>
          <button type="submit" class="w-full py-3 rounded-xl bg-[#168447] hover:bg-[#105B34] text-white font-bold">অর্ডার নিশ্চিত করুন</button>
        </form>
      </div>
    </div>
  `;
}

function renderOrdersPage() {
  const user = getActiveUser();
  if (!user) return renderLoginPage();

  const myOrders =
    user.role === "ADMIN"
      ? state.orders
      : user.role === "FARMER"
      ? state.orders.filter((o) => o.farmerId === user.id || o.buyerId === user.id)
      : state.orders.filter((o) => o.buyerId === user.id);

  const verifyStatusBadge = user.role === "ADMIN"
    ? ""
    : `<span class="px-2.5 py-1 rounded-full text-xs font-bold ${
        user.verificationStatus === "VERIFIED"
          ? "bg-emerald-100 text-emerald-800"
          : user.verificationStatus === "REJECTED"
          ? "bg-red-100 text-red-700"
          : "bg-amber-100 text-amber-800"
      }">${
        user.verificationStatus === "VERIFIED"
          ? "✓ যাচাইকৃত অ্যাকাউন্ট"
          : user.verificationStatus === "REJECTED"
          ? "যাচাই প্রত্যাখ্যাত"
          : "অ্যাডমিন যাচাই অপেক্ষমাণ"
      }</span>`;

  return `
    <div class="space-y-5">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex flex-wrap items-center gap-2">
          <h1 class="text-2xl font-bold text-[#105B34]">📦 আমার অর্ডার ও ট্র্যাকিং (${myOrders.length})</h1>
          ${verifyStatusBadge}
        </div>
        <button onclick="window.kbApp.navigate('browse')" class="px-4 py-2 rounded-xl bg-[#EAF7EF] text-[#105B34] text-xs font-bold">+ আরও ফসল কিনুন</button>
      </div>
      ${
        !myOrders.length
          ? `<div class="bg-white rounded-3xl p-10 text-center border border-dashed border-emerald-200">কোনো অর্ডার পাওয়া যায়নি।</div>`
          : myOrders
              .map((o) => {
                const meta = ORDER_STATUS_META[o.status] || ORDER_STATUS_META.PENDING;
                const canCancel = o.buyerId === user.id && o.status === "PENDING";
                const canReview = o.buyerId === user.id && o.status === "COMPLETED";
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
                      <div>কৃষক: <strong>${escapeHtml(o.farmerName)}</strong> (${escapeHtml(o.farmerPhone || "-")})</div>
                      <div>এলাকা: ${escapeHtml(o.upazila)}, ${escapeHtml(o.district)}</div>
                      <div>মোট: <strong class="text-[#168447]">${formatBdt(o.grandTotal)}</strong></div>
                    </div>
                    <div class="flex flex-wrap gap-2 pt-1">
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
  const verifiedFarmers = farmers.filter((u) => u.verificationStatus === "VERIFIED").length;
  const verifiedBuyers = buyers.filter((u) => u.verificationStatus === "VERIFIED").length;
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
    ["notifications", "🔔 নোটিফিকেশন পাঠান"],
    ["farmers", `কৃষক ও ক্রেতা (${farmers.length + buyers.length})`],
    ["orders", `অর্ডার (${state.orders.length})`],
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
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">মোট কৃষক (${verifiedFarmers} যাচাইকৃত)</div><div class="text-2xl font-extrabold text-[#105B34]">${farmers.length}</div></div>
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">মোট ক্রেতা (${verifiedBuyers} যাচাইকৃত)</div><div class="text-2xl font-extrabold text-[#105B34]">${buyers.length}</div></div>
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">সক্রিয় ফসল</div><div class="text-2xl font-extrabold text-[#105B34]">${state.products.filter((p) => p.status === "ACTIVE").length}</div></div>
        <div class="bg-white rounded-2xl p-5 border border-emerald-100"><div class="text-xs text-gray-500">মোট বিক্রয়</div><div class="text-2xl font-extrabold text-[#168447]">${formatBdt(totalSales)}</div></div>
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
  } else if (tab === "notifications") {
    const nonAdminUsers = state.users.filter((u) => u.role !== "ADMIN");
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
    const pendingFarmersCount = farmers.filter((u) => u.verificationStatus === "PENDING").length;
    const pendingBuyersCount = buyers.filter((u) => u.verificationStatus === "PENDING").length;
    const combinedUsers = (uFilter === "FARMER" ? farmers : uFilter === "BUYER" ? buyers : [...farmers, ...buyers])
      .slice()
      .sort((a, b) => {
        const rank = (s) => (s === "PENDING" ? 0 : s === "REJECTED" ? 1 : 2);
        const diff = rank(a.verificationStatus) - rank(b.verificationStatus);
        return diff !== 0 ? diff : (b.createdAt || 0) - (a.createdAt || 0);
      });

    bodyHtml = `
      <div class="space-y-4">
        <div class="bg-white rounded-2xl p-4 border border-emerald-100 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 class="font-bold text-sm text-[#105B34]">কৃষক ও ক্রেতা যাচাইকরণ এবং তালিকা</h3>
            <p class="text-xs text-gray-600">অপেক্ষমাণ কৃষক: ${pendingFarmersCount} জন • অপেক্ষমাণ ক্রেতা: ${pendingBuyersCount} জন</p>
          </div>
          <div class="flex flex-wrap gap-2">
            <button onclick="window.kbApp.setAdminUserFilter('ALL')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${uFilter === "ALL" ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">সবাই (${farmers.length + buyers.length})</button>
            <button onclick="window.kbApp.setAdminUserFilter('FARMER')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${uFilter === "FARMER" ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">👨‍🌾 কৃষক (${farmers.length})</button>
            <button onclick="window.kbApp.setAdminUserFilter('BUYER')" class="px-3 py-1.5 rounded-xl text-xs font-bold ${uFilter === "BUYER" ? "bg-[#105B34] text-white" : "bg-[#EAF7EF] text-[#105B34]"}">🛍️ ক্রেতা (${buyers.length})</button>
          </div>
        </div>

        <div class="space-y-3">
          ${
            !combinedUsers.length
              ? `<div class="bg-white rounded-3xl p-8 text-center">এই তালিকায় এখনো কোনো নিবন্ধিত কৃষক বা ক্রেতা নেই।</div>`
              : combinedUsers
                  .map((person) => {
                    const isFarmer = person.role === "FARMER";
                    const stColor =
                      person.verificationStatus === "VERIFIED"
                        ? "bg-emerald-100 text-emerald-800"
                        : person.verificationStatus === "REJECTED"
                        ? "bg-red-100 text-red-700"
                        : "bg-amber-100 text-amber-800";
                    return `<div class="bg-white rounded-2xl p-4 border border-emerald-100 flex flex-wrap items-center justify-between gap-3">
                      <div class="space-y-1">
                        <div class="flex flex-wrap items-center gap-2">
                          <span class="font-bold text-sm text-[#105B34]">${isFarmer ? "👨‍🌾" : "🛍️"} ${escapeHtml(person.fullName)} (${escapeHtml(person.phone)})</span>
                          <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${isFarmer ? "bg-emerald-50 text-emerald-700 border border-emerald-200" : "bg-sky-50 text-sky-700 border border-sky-200"}">${isFarmer ? "কৃষক (Farmer)" : "ক্রেতা (Buyer)"}</span>
                          <span class="px-2 py-0.5 rounded-full text-[10px] font-bold ${stColor}">${escapeHtml(person.verificationStatus || "PENDING")}</span>
                        </div>
                        <div class="text-xs text-gray-600">${escapeHtml(person.upazila)}, ${escapeHtml(person.district)}, ${escapeHtml(person.division)} • NID: ${escapeHtml(person.nidMasked || "N/A")}</div>
                        ${isFarmer && person.farmBio ? `<div class="text-[11px] text-gray-500">খামার: ${escapeHtml(person.farmBio)}</div>` : ""}
                      </div>
                      <div class="flex gap-2">
                        ${person.verificationStatus !== "VERIFIED" ? `<button onclick="window.kbApp.adminVerifyUser('${escapeHtml(person.id)}', true)" class="px-3 py-1.5 rounded-xl bg-[#168447] text-white text-xs font-bold">✓ অনুমোদন</button>` : ""}
                        ${person.verificationStatus !== "REJECTED" ? `<button onclick="window.kbApp.adminVerifyUser('${escapeHtml(person.id)}', false)" class="px-3 py-1.5 rounded-xl bg-red-600 text-white text-xs font-bold">✕ বাতিল</button>` : ""}
                      </div>
                    </div>`;
                  })
                  .join("")
          }
        </div>
      </div>
    `;
  } else if (tab === "orders") {
    bodyHtml = `<div class="space-y-3">${state.orders.length ? state.orders.map((o) => `<div class="bg-white rounded-2xl p-4 border border-emerald-100 text-xs flex justify-between"><div><strong>${escapeHtml(o.orderCode)}</strong> — ${escapeHtml(o.itemsSummary)}<br/>কৃষক: ${escapeHtml(o.farmerName)} • ক্রেতা: ${escapeHtml(o.buyerName)}</div><div class="text-right font-bold text-[#168447]">${formatBdt(o.grandTotal)}<br/><span class="text-gray-600">${escapeHtml(o.status)}</span></div></div>`).join("") : `<div class="bg-white rounded-2xl p-8 text-center">কোনো অর্ডার নেই।</div>`}</div>`;
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
    }
    if (key === "district") {
      state.filters.upazila = "";
    }
    renderCurrentView();
  },
  resetFilters() {
    state.filters = { query: "", categoryId: "", division: "", district: "", upazila: "", wholesaleOnly: "", sort: "NEWEST" };
    renderCurrentView();
  },
  onRegDivisionChange(divBn) {
    const distSelect = document.getElementById("reg-district");
    const upzSelect = document.getElementById("reg-upazila");
    const dists = BD_DISTRICTS.filter((d) => d.divisionBn === divBn);
    if (distSelect) {
      distSelect.innerHTML = dists.map((d) => `<option value="${escapeHtml(d.districtBn)}">${escapeHtml(d.districtBn)}</option>`).join("");
    }
    if (upzSelect && dists[0]) {
      upzSelect.innerHTML = dists[0].upazilas.map((u) => `<option value="${escapeHtml(u)}">${escapeHtml(u)}</option>`).join("");
    }
  },
  onRegDistrictChange(distBn) {
    const upzSelect = document.getElementById("reg-upazila");
    const d = BD_DISTRICTS.find((x) => x.districtBn === distBn);
    if (upzSelect && d) {
      upzSelect.innerHTML = d.upazilas.map((u) => `<option value="${escapeHtml(u)}">${escapeHtml(u)}</option>`).join("");
    }
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
  updateFarmerPriceAndStock,
  updateOrderStatus,
  setFarmerTab(t) { state.farmerTab = t; renderCurrentView(); },
  setAdminTab(t) { state.adminTab = t; renderCurrentView(); },
  setAdminUserFilter(f) { state.adminUserFilter = f; renderCurrentView(); },
  setInfoTab(t) { state.infoTab = t; renderCurrentView(); },
  adminVerifyUser,
  adminVerifyFarmer: adminVerifyUser,
  adminResolveComplaint,
  exportCsvReport,
  markVisibleNotificationsRead,
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
