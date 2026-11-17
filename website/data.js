// Krishi Bondhu (কৃষি বন্ধু) — Shared Master Catalogs & Helpers (Web + Android RTDB Parity)

export const RTDB_CONFIG = {
  apiKey: "AIzaSyA3Cd6qJVF8cl7mK4z_7egEdxXcn-hHDoA",
  authDomain: "screen-playbd.firebaseapp.com",
  databaseURL: "https://screen-playbd-default-rtdb.europe-west1.firebasedatabase.app",
  projectId: "screen-playbd",
  storageBucket: "screen-playbd.firebasestorage.app",
  messagingSenderId: "1022188847076",
  appId: "1:1022188847076:web:krishibondhubd"
};

export const SUPERADMIN_CREDENTIALS = {
  id: "admin",
  password: "bondhu",
  userId: "admin_super"
};

export const DEFAULT_AD_CONFIG = {
  adsEnabled: true,
  showAdsLabel: true,
  androidAdsEnabled: true,
  androidBannerId: "",
  androidInterstitialId: "",
  mobileWebAdsEnabled: true,
  mobileAdNetworkPriority: "ALL",
  mobileCustomAdHtml: "",
  mobileDirectLinkUrl: "",
  adsenseEnabled: false,
  adsenseClient: "",
  adsenseTopSlot: "",
  adsenseFeedSlot: "",
  adsenseFooterSlot: "",
  adsterraEnabled: false,
  adsterraBannerKey: "",
  adsterraScriptUrl: "",
  adsterraSocialBarUrl: "",
  monetagEnabled: false,
  monetagZoneId: "",
  monetagScriptUrl: "",
  customAdEnabled: false,
  customAdName: "Ezoic / Media.net / Custom",
  customTopHtml: "",
  customFeedHtml: "",
  customFooterHtml: "",
  sponsorEnabled: true,
  sponsorTitle: "সরাসরি কৃষকের বীজ, সার ও আধুনিক কৃষি পরামর্শ",
  sponsorSubtitle: "কৃষি বন্ধু স্পন্সর স্লট — অ্যাডমিন প্যানেল থেকে আপনার বিজ্ঞাপন নেটওয়ার্ক বা স্পন্সর ব্যানার যুক্ত করুন।",
  sponsorImageUrl: "",
  sponsorTargetUrl: "",
  sponsorCtaText: "বিস্তারিত দেখুন",
  updatedAt: 0
};

export const MASTER_CATEGORIES = [
  { id: "cat_paddy", nameBn: "ধান", nameEn: "Paddy", groupKey: "GRAIN", emoji: "🌾", sortOrder: 1, isActive: true },
  { id: "cat_rice", nameBn: "চাল", nameEn: "Rice", groupKey: "GRAIN", emoji: "🍚", sortOrder: 2, isActive: true },
  { id: "cat_wheat", nameBn: "গম", nameEn: "Wheat", groupKey: "GRAIN", emoji: "🌿", sortOrder: 3, isActive: true },
  { id: "cat_maize", nameBn: "ভুট্টা", nameEn: "Maize", groupKey: "GRAIN", emoji: "🌽", sortOrder: 4, isActive: true },
  { id: "cat_potato", nameBn: "আলু", nameEn: "Potato", groupKey: "VEGETABLE", emoji: "🥔", sortOrder: 5, isActive: true },
  { id: "cat_onion", nameBn: "পেঁয়াজ", nameEn: "Onion", groupKey: "VEGETABLE", emoji: "🧅", sortOrder: 6, isActive: true },
  { id: "cat_garlic", nameBn: "রসুন", nameEn: "Garlic", groupKey: "VEGETABLE", emoji: "🧄", sortOrder: 7, isActive: true },
  { id: "cat_chili", nameBn: "মরিচ", nameEn: "Chili", groupKey: "VEGETABLE", emoji: "🌶️", sortOrder: 8, isActive: true },
  { id: "cat_tomato", nameBn: "টমেটো", nameEn: "Tomato", groupKey: "VEGETABLE", emoji: "🍅", sortOrder: 9, isActive: true },
  { id: "cat_eggplant", nameBn: "বেগুন", nameEn: "Eggplant", groupKey: "VEGETABLE", emoji: "🍆", sortOrder: 10, isActive: true },
  { id: "cat_leafy", nameBn: "শাকসবজি", nameEn: "Leafy Vegetables", groupKey: "VEGETABLE", emoji: "🥬", sortOrder: 11, isActive: true },
  { id: "cat_mango", nameBn: "আম", nameEn: "Mango", groupKey: "FRUIT", emoji: "🥭", sortOrder: 12, isActive: true },
  { id: "cat_jackfruit", nameBn: "কাঁঠাল", nameEn: "Jackfruit", groupKey: "FRUIT", emoji: "🍈", sortOrder: 13, isActive: true },
  { id: "cat_banana", nameBn: "কলা", nameEn: "Banana", groupKey: "FRUIT", emoji: "🍌", sortOrder: 14, isActive: true },
  { id: "cat_lychee", nameBn: "লিচু", nameEn: "Lychee", groupKey: "FRUIT", emoji: "🍒", sortOrder: 15, isActive: true },
  { id: "cat_lentil", nameBn: "মসুর ডাল", nameEn: "Red Lentil", groupKey: "PULSE", emoji: "🫘", sortOrder: 16, isActive: true },
  { id: "cat_mung", nameBn: "মুগ ডাল", nameEn: "Mung Bean", groupKey: "PULSE", emoji: "🫛", sortOrder: 17, isActive: true },
  { id: "cat_mustard", nameBn: "সরিষা", nameEn: "Mustard", groupKey: "OILSEED", emoji: "🌼", sortOrder: 18, isActive: true },
  { id: "cat_sesame", nameBn: "তিল", nameEn: "Sesame", groupKey: "OILSEED", emoji: "🌱", sortOrder: 19, isActive: true },
  { id: "cat_sugarcane", nameBn: "আখ", nameEn: "Sugarcane", groupKey: "OTHER", emoji: "🎋", sortOrder: 20, isActive: true },
  { id: "cat_fish", nameBn: "মাছ", nameEn: "Fish", groupKey: "PROTEIN", emoji: "🐟", sortOrder: 21, isActive: true },
  { id: "cat_milk", nameBn: "দুধ", nameEn: "Milk", groupKey: "PROTEIN", emoji: "🥛", sortOrder: 22, isActive: true },
  { id: "cat_egg", nameBn: "ডিম", nameEn: "Egg", groupKey: "PROTEIN", emoji: "🥚", sortOrder: 23, isActive: true },
  { id: "cat_other", nameBn: "অন্যান্য কৃষিপণ্য", nameEn: "Other Produce", groupKey: "OTHER", emoji: "🧺", sortOrder: 24, isActive: true }
];

export const BD_DIVISIONS = [
  "ঢাকা",
  "চট্টগ্রাম",
  "রাজশাহী",
  "খুলনা",
  "বরিশাল",
  "সিলেট",
  "রংপুর",
  "ময়মনসিংহ"
];

export const BD_DISTRICTS = [
  // ঢাকা বিভাগ (১৩)
  { divisionBn: "ঢাকা", districtBn: "ঢাকা", upazilas: ["সাভার", "ধামরাই", "কেরানীগঞ্জ", "দোহার", "নবাবগঞ্জ"] },
  { divisionBn: "ঢাকা", districtBn: "গাজীপুর", upazilas: ["গাজীপুর সদর", "কালিয়াকৈর", "কালীগঞ্জ", "কাপাসিয়া", "শ্রীপুর"] },
  { divisionBn: "ঢাকা", districtBn: "নারায়ণগঞ্জ", upazilas: ["নারায়ণগঞ্জ সদর", "আড়াইহাজার", "বন্দর", "রূপগঞ্জ", "সোনারগাঁও"] },
  { divisionBn: "ঢাকা", districtBn: "নরসিংদী", upazilas: ["নরসিংদী সদর", "বেলাবো", "মনোহরদী", "পলাশ", "রায়পুরা", "শিবপুর"] },
  { divisionBn: "ঢাকা", districtBn: "মানিকগঞ্জ", upazilas: ["মানিকগঞ্জ সদর", "দৌলতপুর", "ঘিওর", "হরিরামপুর", "সাটুরিয়া", "শিবালয়", "সিংগাইর"] },
  { divisionBn: "ঢাকা", districtBn: "মুন্সীগঞ্জ", upazilas: ["মুন্সীগঞ্জ সদর", "গজারিয়া", "লৌহজং", "সিরাজদিখান", "শ্রীনগর", "টঙ্গীবাড়ী"] },
  { divisionBn: "ঢাকা", districtBn: "টাঙ্গাইল", upazilas: ["টাঙ্গাইল সদর", "বাসাইল", "ভুঞাপুর", "দেলদুয়ার", "ঘাটাইল", "গোপালপুর", "কালিহাতী", "মধুপুর", "মির্জাপুর", "সখিপুর"] },
  { divisionBn: "ঢাকা", districtBn: "কিশোরগঞ্জ", upazilas: ["কিশোরগঞ্জ সদর", "বাজিতপুর", "ভৈরব", "হোসেনপুর", "ইটনা", "করিমগঞ্জ", "কটিয়াদী", "নিকলী", "পাকুন্দিয়া"] },
  { divisionBn: "ঢাকা", districtBn: "ফরিদপুর", upazilas: ["ফরিদপুর সদর", "আলফাডাঙ্গা", "ভাঙ্গা", "বোয়ালমারী", "চরভদ্রাসন", "মধুখালী", "নগরকান্দা", "সদরপুর", "সালথা"] },
  { divisionBn: "ঢাকা", districtBn: "মাদারীপুর", upazilas: ["মাদারীপুর সদর", "কালকিনি", "রাজৈর", "শিবচর", "ডাসার"] },
  { divisionBn: "ঢাকা", districtBn: "শরীয়তপুর", upazilas: ["শরীয়তপুর সদর", "ভেদরগঞ্জ", "ডামুড্যা", "গোসাইরহাট", "নড়িয়া", "জাজিরা"] },
  { divisionBn: "ঢাকা", districtBn: "গোপালগঞ্জ", upazilas: ["গোপালগঞ্জ সদর", "কাশিয়ানী", "কোটালীপাড়া", "মুকসুদপুর", "টুঙ্গিপাড়া"] },
  { divisionBn: "ঢাকা", districtBn: "রাজবাড়ী", upazilas: ["রাজবাড়ী সদর", "বালিয়াকান্দি", "গোয়ালন্দ", "পাংশা", "কালুখালী"] },

  // চট্টগ্রাম বিভাগ (১১)
  { divisionBn: "চট্টগ্রাম", districtBn: "চট্টগ্রাম", upazilas: ["সীতাকুণ্ড", "মিরসরাই", "হাটহাজারী", "পটিয়া", "রাউজান", "রাঙ্গুনিয়া", "আনোয়ারা", "চন্দনাইশ", "বাঁশখালী"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "কক্সবাজার", upazilas: ["কক্সবাজার সদর", "চকরিয়া", "পেকুয়া", "রামু", "টেকনাফ", "উখিয়া", "মহেশখালী"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "কুমিল্লা", upazilas: ["কুমিল্লা আদর্শ সদর", "বরুড়া", "চান্দিনা", "চৌদ্দগ্রাম", "দাউদকান্দি", "দেবিদ্বার", "লাকসাম", "মুরাদনগর", "নাঙ্গলকোট"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "ব্রাহ্মণবাড়িয়া", upazilas: ["ব্রাহ্মণবাড়িয়া সদর", "আখাউড়া", "বাঞ্ছারামপুর", "কসবা", "নবীনগর", "নাসিরনগর", "সরাইল"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "চাঁদপুর", upazilas: ["চাঁদপুর সদর", "ফরিদগঞ্জ", "হাইমচর", "হাজীগঞ্জ", "কচুয়া", "মতলব উত্তর", "মতলব দক্ষিণ", "শাহরাস্তি"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "নোয়াখালী", upazilas: ["নোয়াখালী সদর", "বেগমগঞ্জ", "চাটখিল", "কোম্পানীগঞ্জ", "হাতিয়া", "সেনবাগ", "সোনাইমুড়ী", "সুবর্ণচর"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "ফেনী", upazilas: ["ফেনী সদর", "ছাগলনাইয়া", "দাগনভূঞা", "ফুলগাজী", "পরশুরাম", "সোনাগাজী"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "লক্ষ্মীপুর", upazilas: ["লক্ষ্মীপুর সদর", "কমলনগর", "রায়পুর", "রামগঞ্জ", "রামগতি"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "রাঙ্গামাটি", upazilas: ["রাঙ্গামাটি সদর", "বাঘাইছড়ি", "বরকল", "কাপ্তাই", "লংগদু", "নানিয়ারচর"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "খাগড়াছড়ি", upazilas: ["খাগড়াছড়ি সদর", "দীঘিনালা", "মাটিরাঙ্গা", "মানিকছড়ি", "পানছড়ি", "রামগড়"] },
  { divisionBn: "চট্টগ্রাম", districtBn: "বান্দরবান", upazilas: ["বান্দরবান সদর", "আলীকদম", "লামা", "নাইক্ষ্যংছড়ি", "রুমা", "থানচি"] },

  // রাজশাহী বিভাগ (৮)
  { divisionBn: "রাজশাহী", districtBn: "রাজশাহী", upazilas: ["পবা", "বাঘা", "বাগমারা", "চারঘাট", "দুর্গাপুর", "গোদাগাড়ী", "মোহনপুর", "পুঠিয়া", "তানোর"] },
  { divisionBn: "রাজশাহী", districtBn: "বগুড়া", upazilas: ["বগুড়া সদর", "আদমদীঘি", "ধুনট", "দুপচাঁচিয়া", "গাবতলী", "কাহালু", "নন্দীগ্রাম", "শাজাহানপুর", "শেরপুর", "শিবগঞ্জ"] },
  { divisionBn: "রাজশাহী", districtBn: "পাবনা", upazilas: ["পাবনা সদর", "আটঘরিয়া", "বেড়া", "ভাঙ্গুড়া", "চাটমোহর", "ফরিদপুর", "ঈশ্বরদী", "সাঁথিয়া", "সুজানগর"] },
  { divisionBn: "রাজশাহী", districtBn: "সিরাজগঞ্জ", upazilas: ["সিরাজগঞ্জ সদর", "বেলকুচি", "চৌহালি", "কামারখন্দ", "কাজীপুর", "রায়গঞ্জ", "শাহজাদপুর", "তাড়াশ", "উল্লাপাড়া"] },
  { divisionBn: "রাজশাহী", districtBn: "নাটোর", upazilas: ["নাটোর সদর", "বাগাতিপাড়া", "বড়াইগ্রাম", "গুরুদাসপুর", "লালপুর", "সিংড়া", "নলডাঙ্গা"] },
  { divisionBn: "রাজশাহী", districtBn: "নওগাঁ", upazilas: ["নওগাঁ সদর", "আত্রাই", "বদলগাছী", "ধামইরহাট", "মান্দা", "মহাদেবপুর", "নিয়ামতপুর", "পত্নীতলা", "রাণীনগর", "সাপাহার"] },
  { divisionBn: "রাজশাহী", districtBn: "চাঁপাইনবাবগঞ্জ", upazilas: ["চাঁপাইনবাবগঞ্জ সদর", "ভোলাহাট", "গোমস্তাপুর", "নাচোল", "শিবগঞ্জ"] },
  { divisionBn: "রাজশাহী", districtBn: "জয়পুরহাট", upazilas: ["জয়পুরহাট সদর", "আক্কেলপুর", "কালাই", "ক্ষেতলাল", "পাঁচবিবি"] },

  // খুলনা বিভাগ (১০)
  { divisionBn: "খুলনা", districtBn: "কুষ্টিয়া", upazilas: ["কুষ্টিয়া সদর", "কুমারখালী", "খোকসা", "মিরপুর", "ভেড়ামারা", "দৌলতপুর"] },
  { divisionBn: "খুলনা", districtBn: "খুলনা", upazilas: ["বটিয়াঘাটা", "দাকোপ", "ডুমুরিয়া", "দিঘলিয়া", "কয়রা", "পাইকগাছা", "ফুলতলা", "রূপসা", "তেরখাদা"] },
  { divisionBn: "খুলনা", districtBn: "যশোর", upazilas: ["যশোর সদর", "অভয়নগর", "বাঘারপাড়া", "চৌগাছা", "ঝিকরগাছা", "কেশবপুর", "মণিরামপুর", "শার্শা"] },
  { divisionBn: "খুলনা", districtBn: "ঝিনাইদহ", upazilas: ["ঝিনাইদহ সদর", "হরিণাকুণ্ডু", "কালীগঞ্জ", "কোটচাঁদপুর", "মহেশপুর", "শৈলকুপা"] },
  { divisionBn: "খুলনা", districtBn: "চুয়াডাঙ্গা", upazilas: ["চুয়াডাঙ্গা সদর", "আলমডাঙ্গা", "দামুড়হুদা", "জীবননগর"] },
  { divisionBn: "খুলনা", districtBn: "মেহেরপুর", upazilas: ["মেহেরপুর সদর", "গাংনী", "মুজিবনগর"] },
  { divisionBn: "খুলনা", districtBn: "মাগুরা", upazilas: ["মাগুরা সদর", "মহম্মদপুর", "শালিখা", "শ্রীপুর"] },
  { divisionBn: "খুলনা", districtBn: "নড়াইল", upazilas: ["নড়াইল সদর", "কালিয়া", "লোহাগড়া"] },
  { divisionBn: "খুলনা", districtBn: "সাতক্ষীরা", upazilas: ["সাতক্ষীরা সদর", "আশাশুনি", "দেবহাটা", "কলারোয়া", "কালীগঞ্জ", "শ্যামনগর", "তালা"] },
  { divisionBn: "খুলনা", districtBn: "বাগেরহাট", upazilas: ["বাগেরহাট সদর", "চিতলমারী", "ফকিরহাট", "কচুয়া", "মোল্লারহাট", "মোংলা", "মোড়েলগঞ্জ", "রামপাল", "শরণখোলা"] },

  // বরিশাল বিভাগ (৬)
  { divisionBn: "বরিশাল", districtBn: "বরিশাল", upazilas: ["বরিশাল সদর", "আগৈলঝাড়া", "বাবুগঞ্জ", "বাকেরগঞ্জ", "বানারীপাড়া", "গৌরনদী", "হিজলা", "মেহেন্দিগঞ্জ", "মুলাদী", "উজিরপুর"] },
  { divisionBn: "বরিশাল", districtBn: "পটুয়াখালী", upazilas: ["পটুয়াখালী সদর", "বাউফল", "দশমিনা", "দুমকি", "গলাচিপা", "কলাপাড়া", "মির্জাগঞ্জ", "রাঙ্গাবালী"] },
  { divisionBn: "বরিশাল", districtBn: "ভোলা", upazilas: ["ভোলা সদর", "বোরহানউদ্দিন", "চরফ্যাশন", "দৌলতখান", "লালমোহন", "মনপুরা", "তজুমদ্দিন"] },
  { divisionBn: "বরিশাল", districtBn: "পিরোজপুর", upazilas: ["পিরোজপুর সদর", "ভান্ডারিয়া", "কাউখালী", "মঠবাড়িয়া", "নাজিরপুর", "নেছারাবাদ", "ইন্দুরকানী"] },
  { divisionBn: "বরিশাল", districtBn: "বরগুনা", upazilas: ["বরগুনা সদর", "আমতলী", "বামনা", "বেতাগী", "পাথরঘাটা", "তালতলী"] },
  { divisionBn: "বরিশাল", districtBn: "ঝালকাঠি", upazilas: ["ঝালকাঠি সদর", "কাঁঠালিয়া", "নলছিটি", "রাজাপুর"] },

  // সিলেট বিভাগ (৪)
  { divisionBn: "সিলেট", districtBn: "সিলেট", upazilas: ["সিলেট সদর", "বালাগঞ্জ", "বিয়ানীবাজার", "বিশ্বনাথ", "কোম্পানীগঞ্জ", "ফেঞ্চুগঞ্জ", "গোলাপগঞ্জ", "গোয়াইনঘাট", "জৈন্তাপুর", "কানাইঘাট"] },
  { divisionBn: "সিলেট", districtBn: "সুনামগঞ্জ", upazilas: ["সুনামগঞ্জ সদর", "বিশ্বম্ভরপুর", "ছাতক", "দিরাই", "ধর্মপাশা", "দোয়ারাবাজার", "জগন্নাথপুর", "জামালগঞ্জ", "শাল্লা", "তাহিরপুর"] },
  { divisionBn: "সিলেট", districtBn: "হবিগঞ্জ", upazilas: ["হবিগঞ্জ সদর", "আজমিরীগঞ্জ", "বাহুবল", "বানিয়াচং", "চুনারুঘাট", "লাখাই", "মাধবপুর", "নবীগঞ্জ"] },
  { divisionBn: "সিলেট", districtBn: "মৌলভীবাজার", upazilas: ["মৌলভীবাজার সদর", "বড়লেখা", "জুড়ী", "কমলগঞ্জ", "কুলাউড়া", "রাজনগর", "শ্রীমঙ্গল"] },

  // রংপুর বিভাগ (৮)
  { divisionBn: "রংপুর", districtBn: "রংপুর", upazilas: ["রংপুর সদর", "বদরগঞ্জ", "গঙ্গাচড়া", "কাউনিয়া", "মিঠাপুকুর", "পীরগাছা", "পীরগঞ্জ", "তারাগঞ্জ"] },
  { divisionBn: "রংপুর", districtBn: "দিনাজপুর", upazilas: ["দিনাজপুর সদর", "বিরামপুর", "বীরগঞ্জ", "বিরল", "বোচাগঞ্জ", "চিরিরবন্দর", "ফুলবাড়ী", "ঘোড়াঘাট", "হাকিমপুর", "কাহারোল", "পার্বতীপুর"] },
  { divisionBn: "রংপুর", districtBn: "কুড়িগ্রাম", upazilas: ["কুড়িগ্রাম সদর", "ভুরুঙ্গামারী", "চর রাজিবপুর", "চিলমারী", "ফুলবাড়ী", "নাগেশ্বরী", "রাজারহাট", "রৌমারী", "উলিপুর"] },
  { divisionBn: "রংপুর", districtBn: "গাইবান্ধা", upazilas: ["গাইবান্ধা সদর", "ফুলছড়ি", "গোবিন্দগঞ্জ", "পলাশবাড়ী", "সাদুল্লাপুর", "সুন্দরগঞ্জ", "সাঘাটা"] },
  { divisionBn: "রংপুর", districtBn: "নীলফামারী", upazilas: ["নীলফামারী সদর", "ডিমলা", "ডোমার", "জলঢাকা", "কিশোরগঞ্জ", "সৈয়দপুর"] },
  { divisionBn: "রংপুর", districtBn: "লালমনিরহাট", upazilas: ["লালমনিরহাট সদর", "আদিতমারী", "হাতীবান্ধা", "কালীগঞ্জ", "পাটগ্রাম"] },
  { divisionBn: "রংপুর", districtBn: "ঠাকুরগাঁও", upazilas: ["ঠাকুরগাঁও সদর", "বালিয়াডাঙ্গী", "হরিপুর", "পীরগঞ্জ", "রাণীশংকৈল"] },
  { divisionBn: "রংপুর", districtBn: "পঞ্চগড়", upazilas: ["পঞ্চগড় সদর", "আটোয়ারী", "বোদা", "দেবীগঞ্জ", "তেঁতুলিয়া"] },

  // ময়মনসিংহ বিভাগ (৪)
  { divisionBn: "ময়মনসিংহ", districtBn: "ময়মনসিংহ", upazilas: ["ময়মনসিংহ সদর", "ভালুকা", "ধোবাউড়া", "ফুলবাড়িয়া", "গফরগাঁও", "গৌরীপুর", "হালুয়াঘাট", "ঈশ্বরগঞ্জ", "মুক্তাগাছা", "নান্দাইল", "ত্রিশাল"] },
  { divisionBn: "ময়মনসিংহ", districtBn: "জামালপুর", upazilas: ["জামালপুর সদর", "বকশীগঞ্জ", "দেওয়ানগঞ্জ", "ইসলামপুর", "মাদারগঞ্জ", "মেলান্দহ", "সরিষাবাড়ী"] },
  { divisionBn: "ময়মনসিংহ", districtBn: "নেত্রকোনা", upazilas: ["নেত্রকোনা সদর", "আটপাড়া", "বারহাট্টা", "দুর্গাপুর", "কলমাকান্দা", "কেন্দুয়া", "খালিয়াজুরী", "মদন", "মোহনগঞ্জ", "পূর্বধলা"] },
  { divisionBn: "ময়মনসিংহ", districtBn: "শেরপুর", upazilas: ["শেরপুর সদর", "ঝিনাইগাতী", "নকলা", "নালিতাবাড়ী", "শ্রীবরদী"] }
];

export const MON_TO_KG = 40;
export const FIXED_ACTIVATION_FEE_BDT = 300;
export const FIXED_VALIDITY_YEARS = 2;

export const DEFAULT_PAYMENT_SETTINGS = {
  id: "default",
  bKashNumber: "01756144900",
  bKashAccountType: "PERSONAL",
  nagadNumber: "01756144900",
  nagadAccountType: "PERSONAL",
  farmerFee: 300,
  buyerFee: 300,
  renewalFee: 300,
  validityYears: 2,
  paymentInstructions:
    "নিচের বিকাশ বা নগদ নম্বরে ৩০০ টাকা Send Money / Payment করে আপনার প্রেরক মোবাইল নম্বর, ট্রানজেকশন আইডি (TxID) এবং পেমেন্ট রসিদ/স্ক্রিনশট জমা দিন। অ্যাডমিন যাচাইয়ের পর ২ বছরের জন্য অ্যাকাউন্ট সক্রিয় হবে।",
  notify30Days: true,
  notify7Days: true,
  notifyOnExpiry: true,
  updatedAt: 0
};

export const ACCOUNT_STATUS_META = {
  PENDING_REGISTRATION: { labelBn: "নিবন্ধন অসম্পূর্ণ", labelEn: "Pending Registration", color: "bg-gray-100 text-gray-700" },
  PAYMENT_PENDING: { labelBn: "পেমেন্ট অপেক্ষমান", labelEn: "Payment Pending", color: "bg-amber-100 text-amber-800" },
  PAYMENT_UNDER_REVIEW: { labelBn: "পেমেন্ট যাচাইাধীন", labelEn: "Under Review", color: "bg-sky-100 text-sky-800" },
  ACTIVE: { labelBn: "সক্রিয় (Active)", labelEn: "Active", color: "bg-emerald-100 text-emerald-800" },
  PAYMENT_REJECTED: { labelBn: "পেমেন্ট প্রত্যাখ্যাত", labelEn: "Payment Rejected", color: "bg-red-100 text-red-700" },
  EXPIRED: { labelBn: "মেয়াদোত্তীর্ণ (Expired)", labelEn: "Expired", color: "bg-orange-100 text-orange-800" },
  SUSPENDED: { labelBn: "স্থগিত (Suspended)", labelEn: "Suspended", color: "bg-red-200 text-red-900" }
};

export const ACTIVATION_PAYMENT_STATUS_META = {
  PENDING: { labelBn: "যাচাই অপেক্ষমান (Pending)", labelEn: "Pending", color: "bg-amber-100 text-amber-800" },
  APPROVED: { labelBn: "অনুমোদিত (Approved)", labelEn: "Approved", color: "bg-emerald-100 text-emerald-800" },
  REJECTED: { labelBn: "প্রত্যাখ্যাত (Rejected)", labelEn: "Rejected", color: "bg-red-100 text-red-700" },
  RESUBMISSION_REQUESTED: { labelBn: "পুনঃজমা প্রয়োজন", labelEn: "Resubmission Requested", color: "bg-purple-100 text-purple-800" }
};

export const ORDER_STATUS_META = {
  PENDING: { labelBn: "অপেক্ষমান", labelEn: "Pending", color: "bg-amber-100 text-amber-800" },
  ACCEPTED: { labelBn: "গৃহীত", labelEn: "Accepted", color: "bg-emerald-100 text-emerald-800" },
  REJECTED: { labelBn: "প্রত্যাখ্যাত", labelEn: "Rejected", color: "bg-red-100 text-red-700" },
  PROCESSING: { labelBn: "প্রস্তুত হচ্ছে", labelEn: "Processing", color: "bg-sky-100 text-sky-800" },
  READY_FOR_PICKUP: { labelBn: "সংগ্রহের জন্য প্রস্তুত", labelEn: "Ready for Pickup", color: "bg-teal-100 text-teal-800" },
  READY: { labelBn: "সংগ্রহের জন্য প্রস্তুত", labelEn: "Ready", color: "bg-teal-100 text-teal-800" },
  SHIPPED: { labelBn: "পথে রয়েছে", labelEn: "Shipped", color: "bg-indigo-100 text-indigo-800" },
  DELIVERED: { labelBn: "পৌঁছেছে", labelEn: "Delivered", color: "bg-cyan-100 text-cyan-800" },
  CONFIRMED: { labelBn: "চূড়ান্ত নিশ্চিত", labelEn: "Confirmed", color: "bg-emerald-600 text-white" },
  COMPLETED: { labelBn: "সম্পন্ন", labelEn: "Completed", color: "bg-green-100 text-green-800" },
  CANCELLED: { labelBn: "বাতিল", labelEn: "Cancelled", color: "bg-gray-200 text-gray-700" },
  DISPUTED: { labelBn: "বিরোধপূর্ণ", labelEn: "Disputed", color: "bg-rose-100 text-rose-800" }
};

// Mon-only selling unit (1 মণ = 40 কেজি); KG option is removed platform-wide
export const UNITS = ["মণ"];

export function isAllowedUnit(unit) {
  const clean = String(unit || "").trim();
  return clean === "মণ" || clean === "মন" || clean.toUpperCase() === "MON" || clean.toLowerCase() === "maund";
}

export function formatDateBn(ts) {
  if (!ts || Number(ts) <= 0) return "—";
  try {
    return new Date(Number(ts)).toLocaleDateString("bn-BD", {
      day: "2-digit",
      month: "short",
      year: "numeric"
    });
  } catch (_) {
    return new Date(Number(ts)).toLocaleDateString();
  }
}

export function daysUntilExpiry(user, now = Date.now()) {
  if (!user || !user.expiryDate || Number(user.expiryDate) <= 0) return null;
  const diff = Number(user.expiryDate) - now;
  return diff <= 0 ? 0 : Math.ceil(diff / (24 * 60 * 60 * 1000));
}

export function isMarketplaceActive(user, now = Date.now()) {
  if (!user) return false;
  if (user.role === "ADMIN") return true;
  if ((user.accountStatus || "PAYMENT_PENDING") !== "ACTIVE") return false;
  return !user.expiryDate || Number(user.expiryDate) > now;
}

export function buildInitialSeedLocations() {
  const list = [];
  let idx = 1;
  for (const d of BD_DISTRICTS) {
    const upzs = d.districtBn === "কুষ্টিয়া" ? d.upazilas : [d.upazilas[0]];
    for (const u of upzs) {
      list.push({
        id: `loc_${idx++}`,
        divisionBn: d.divisionBn,
        districtBn: d.districtBn,
        upazilaBn: u,
        unionBn: "সদর ইউনিয়ন",
        villageBn: "",
        isActive: true
      });
    }
  }
  return list;
}

// Exact SHA-256("KB_BD_SALT_2026:" + rawPassword) matching Android SecurityHashUtil
export async function hashPassword(rawPassword, salt = "KB_BD_SALT_2026") {
  const input = `${salt}:${rawPassword}`;
  if (typeof crypto !== "undefined" && crypto.subtle && typeof TextEncoder !== "undefined") {
    try {
      const buf = new TextEncoder().encode(input);
      const digest = await crypto.subtle.digest("SHA-256", buf);
      return Array.from(new Uint8Array(digest))
        .map((b) => b.toString(16).padStart(2, "0"))
        .join("");
    } catch (_) {
      // Fallback below
    }
  }
  return sha256Fallback(input);
}

// Pure-JS SHA-256 fallback for insecure HTTP / file:// preview contexts
function sha256Fallback(ascii) {
  function rightRotate(value, amount) {
    return (value >>> amount) | (value << (32 - amount));
  }
  const bytes = new TextEncoder().encode(ascii);
  const words = [];
  const asciiBitLength = bytes.length * 8;
  let hash = [];
  const k = [];
  let primeCounter = 0;
  const isComposite = {};
  for (let candidate = 2; primeCounter < 64; candidate++) {
    if (!isComposite[candidate]) {
      for (let i = 0; i < 313; i += candidate) isComposite[i] = candidate;
      hash[primeCounter] = (Math.pow(candidate, 0.5) * 0x100000000) | 0;
      k[primeCounter++] = (Math.pow(candidate, 1 / 3) * 0x100000000) | 0;
    }
  }
  const padded = new Uint8Array(((bytes.length + 9 + 63) >> 6) << 6);
  padded.set(bytes);
  padded[bytes.length] = 0x80;
  const view = new DataView(padded.buffer);
  view.setUint32(padded.length - 4, asciiBitLength, false);

  for (let i = 0; i < padded.length; i += 64) {
    const w = [];
    for (let j = 0; j < 16; j++) w[j] = view.getUint32(i + j * 4, false);
    for (let j = 16; j < 64; j++) {
      const s0 = rightRotate(w[j - 15], 7) ^ rightRotate(w[j - 15], 18) ^ (w[j - 15] >>> 3);
      const s1 = rightRotate(w[j - 2], 17) ^ rightRotate(w[j - 2], 19) ^ (w[j - 2] >>> 10);
      w[j] = (w[j - 16] + s0 + w[j - 7] + s1) | 0;
    }
    let [a, b, c, d, e, f, g, h] = hash;
    for (let j = 0; j < 64; j++) {
      const S1 = rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25);
      const ch = (e & f) ^ (~e & g);
      const temp1 = (h + S1 + ch + k[j] + w[j]) | 0;
      const S0 = rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (S0 + maj) | 0;
      h = g; g = f; f = e; e = (d + temp1) | 0;
      d = c; c = b; b = a; a = (temp1 + temp2) | 0;
    }
    hash = [
      (hash[0] + a) | 0, (hash[1] + b) | 0, (hash[2] + c) | 0, (hash[3] + d) | 0,
      (hash[4] + e) | 0, (hash[5] + f) | 0, (hash[6] + g) | 0, (hash[7] + h) | 0
    ];
  }
  return hash.map((v) => (v >>> 0).toString(16).padStart(8, "0")).join("");
}

export function formatBdt(amount) {
  const num = Number(amount || 0);
  return `৳${num % 1 === 0 ? Math.round(num).toLocaleString("en-IN") : num.toFixed(2)}`;
}

export function formatQty(q) {
  const num = Number(q || 0);
  return num % 1 === 0 ? String(Math.round(num)) : num.toFixed(1);
}

export function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function shortId(prefix) {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return `${prefix}_${crypto.randomUUID().slice(0, 8)}`;
  }
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`;
}

// Approximate geographic centroids (lat, lng) for all 64 districts of Bangladesh (matching Android LocationHelper)
export const BD_DISTRICT_CENTROIDS = [
  // ঢাকা
  { divisionBn: "ঢাকা", districtBn: "ঢাকা", lat: 23.8103, lng: 90.4125 },
  { divisionBn: "ঢাকা", districtBn: "গাজীপুর", lat: 24.0023, lng: 90.4264 },
  { divisionBn: "ঢাকা", districtBn: "নারায়ণগঞ্জ", lat: 23.6238, lng: 90.5000 },
  { divisionBn: "ঢাকা", districtBn: "নরসিংদী", lat: 23.9229, lng: 90.7177 },
  { divisionBn: "ঢাকা", districtBn: "মানিকগঞ্জ", lat: 23.8644, lng: 90.0047 },
  { divisionBn: "ঢাকা", districtBn: "মুন্সীগঞ্জ", lat: 23.5422, lng: 90.5305 },
  { divisionBn: "ঢাকা", districtBn: "টাঙ্গাইল", lat: 24.2513, lng: 89.9167 },
  { divisionBn: "ঢাকা", districtBn: "কিশোরগঞ্জ", lat: 24.4449, lng: 90.7766 },
  { divisionBn: "ঢাকা", districtBn: "ফরিদপুর", lat: 23.6071, lng: 89.8429 },
  { divisionBn: "ঢাকা", districtBn: "মাদারীপুর", lat: 23.1641, lng: 90.1897 },
  { divisionBn: "ঢাকা", districtBn: "শরীয়তপুর", lat: 23.2423, lng: 90.4348 },
  { divisionBn: "ঢাকা", districtBn: "গোপালগঞ্জ", lat: 23.0051, lng: 89.8266 },
  { divisionBn: "ঢাকা", districtBn: "রাজবাড়ী", lat: 23.7574, lng: 89.6445 },
  // চট্টগ্রাম
  { divisionBn: "চট্টগ্রাম", districtBn: "চট্টগ্রাম", lat: 22.3569, lng: 91.7832 },
  { divisionBn: "চট্টগ্রাম", districtBn: "কক্সবাজার", lat: 21.4272, lng: 92.0058 },
  { divisionBn: "চট্টগ্রাম", districtBn: "কুমিল্লা", lat: 23.4607, lng: 91.1809 },
  { divisionBn: "চট্টগ্রাম", districtBn: "ব্রাহ্মণবাড়িয়া", lat: 23.9571, lng: 91.1119 },
  { divisionBn: "চট্টগ্রাম", districtBn: "চাঁদপুর", lat: 23.2333, lng: 90.6713 },
  { divisionBn: "চট্টগ্রাম", districtBn: "নোয়াখালী", lat: 22.8696, lng: 91.0994 },
  { divisionBn: "চট্টগ্রাম", districtBn: "ফেনী", lat: 23.0159, lng: 91.3976 },
  { divisionBn: "চট্টগ্রাম", districtBn: "লক্ষ্মীপুর", lat: 22.9425, lng: 90.8412 },
  { divisionBn: "চট্টগ্রাম", districtBn: "রাঙ্গামাটি", lat: 22.7324, lng: 92.2985 },
  { divisionBn: "চট্টগ্রাম", districtBn: "খাগড়াছড়ি", lat: 23.1193, lng: 91.9847 },
  { divisionBn: "চট্টগ্রাম", districtBn: "বান্দরবান", lat: 22.1953, lng: 92.2184 },
  // রাজশাহী
  { divisionBn: "রাজশাহী", districtBn: "রাজশাহী", lat: 24.3745, lng: 88.6042 },
  { divisionBn: "রাজশাহী", districtBn: "বগুড়া", lat: 24.8465, lng: 89.3773 },
  { divisionBn: "রাজশাহী", districtBn: "পাবনা", lat: 24.0064, lng: 89.2372 },
  { divisionBn: "রাজশাহী", districtBn: "সিরাজগঞ্জ", lat: 24.4534, lng: 89.7007 },
  { divisionBn: "রাজশাহী", districtBn: "নাটোর", lat: 24.4206, lng: 89.0003 },
  { divisionBn: "রাজশাহী", districtBn: "নওগাঁ", lat: 24.7936, lng: 88.9318 },
  { divisionBn: "রাজশাহী", districtBn: "চাঁপাইনবাবগঞ্জ", lat: 24.5965, lng: 88.2775 },
  { divisionBn: "রাজশাহী", districtBn: "জয়পুরহাট", lat: 25.0968, lng: 89.0227 },
  // খুলনা
  { divisionBn: "খুলনা", districtBn: "কুষ্টিয়া", lat: 23.9013, lng: 89.1205 },
  { divisionBn: "খুলনা", districtBn: "খুলনা", lat: 22.8456, lng: 89.5403 },
  { divisionBn: "খুলনা", districtBn: "যশোর", lat: 23.1634, lng: 89.2182 },
  { divisionBn: "খুলনা", districtBn: "ঝিনাইদহ", lat: 23.5448, lng: 89.1539 },
  { divisionBn: "খুলনা", districtBn: "চুয়াডাঙ্গা", lat: 23.6402, lng: 88.8418 },
  { divisionBn: "খুলনা", districtBn: "মেহেরপুর", lat: 23.7622, lng: 88.6318 },
  { divisionBn: "খুলনা", districtBn: "মাগুরা", lat: 23.4873, lng: 89.4199 },
  { divisionBn: "খুলনা", districtBn: "নড়াইল", lat: 23.1725, lng: 89.5127 },
  { divisionBn: "খুলনা", districtBn: "সাতক্ষীরা", lat: 22.7185, lng: 89.0705 },
  { divisionBn: "খুলনা", districtBn: "বাগেরহাট", lat: 22.6516, lng: 89.7859 },
  // বরিশাল
  { divisionBn: "বরিশাল", districtBn: "বরিশাল", lat: 22.7010, lng: 90.3535 },
  { divisionBn: "বরিশাল", districtBn: "পটুয়াখালী", lat: 22.3596, lng: 90.3299 },
  { divisionBn: "বরিশাল", districtBn: "ভোলা", lat: 22.6859, lng: 90.6482 },
  { divisionBn: "বরিশাল", districtBn: "পিরোজপুর", lat: 22.5841, lng: 89.9720 },
  { divisionBn: "বরিশাল", districtBn: "বরগুনা", lat: 22.1592, lng: 90.1256 },
  { divisionBn: "বরিশাল", districtBn: "ঝালকাঠি", lat: 22.6406, lng: 90.1987 },
  // সিলেট
  { divisionBn: "সিলেট", districtBn: "সিলেট", lat: 24.8949, lng: 91.8687 },
  { divisionBn: "সিলেট", districtBn: "সুনামগঞ্জ", lat: 25.0658, lng: 91.3950 },
  { divisionBn: "সিলেট", districtBn: "হবিগঞ্জ", lat: 24.3749, lng: 91.4155 },
  { divisionBn: "সিলেট", districtBn: "মৌলভীবাজার", lat: 24.4829, lng: 91.7774 },
  // রংপুর
  { divisionBn: "রংপুর", districtBn: "রংপুর", lat: 25.7439, lng: 89.2752 },
  { divisionBn: "রংপুর", districtBn: "দিনাজপুর", lat: 25.6217, lng: 88.6355 },
  { divisionBn: "রংপুর", districtBn: "কুড়িগ্রাম", lat: 25.8054, lng: 89.6362 },
  { divisionBn: "রংপুর", districtBn: "গাইবান্ধা", lat: 25.3288, lng: 89.5281 },
  { divisionBn: "রংপুর", districtBn: "নীলফামারী", lat: 25.9318, lng: 88.8560 },
  { divisionBn: "রংপুর", districtBn: "লালমনিরহাট", lat: 25.9923, lng: 89.2847 },
  { divisionBn: "রংপুর", districtBn: "ঠাকুরগাঁও", lat: 26.0337, lng: 88.4617 },
  { divisionBn: "রংপুর", districtBn: "পঞ্চগড়", lat: 26.3411, lng: 88.5542 },
  // ময়মনসিংহ
  { divisionBn: "ময়মনসিংহ", districtBn: "ময়মনসিংহ", lat: 24.7471, lng: 90.4203 },
  { divisionBn: "ময়মনসিংহ", districtBn: "জামালপুর", lat: 24.9375, lng: 89.9378 },
  { divisionBn: "ময়মনসিংহ", districtBn: "নেত্রকোনা", lat: 24.8709, lng: 90.7279 },
  { divisionBn: "ময়মনসিংহ", districtBn: "শেরপুর", lat: 25.0205, lng: 90.0153 }
];

export const ENGLISH_DISTRICT_ALIASES = {
  dhaka: "ঢাকা", dakka: "ঢাকা", savar: "ঢাকা",
  gazipur: "গাজীপুর", narayanganj: "নারায়ণগঞ্জ", narsingdi: "নরসিংদী",
  manikganj: "মানিকগঞ্জ", munshiganj: "মুন্সীগঞ্জ", tangail: "টাঙ্গাইল",
  kishoreganj: "কিশোরগঞ্জ", faridpur: "ফরিদপুর", madaripur: "মাদারীপুর",
  shariatpur: "শরীয়তপুর", gopalganj: "গোপালগঞ্জ", rajbari: "রাজবাড়ী",
  chattogram: "চট্টগ্রাম", chittagong: "চট্টগ্রাম", "cox's bazar": "কক্সবাজার",
  "coxs bazar": "কক্সবাজার", cumilla: "কুমিল্লা", comilla: "কুমিল্লা",
  brahmanbaria: "ব্রাহ্মণবাড়িয়া", chandpur: "চাঁদপুর", noakhali: "নোয়াখালী",
  feni: "ফেনী", lakshmipur: "লক্ষ্মীপুর", rangamati: "রাঙ্গামাটি",
  khagrachari: "খাগড়াছড়ি", bandarban: "বান্দরবান",
  rajshahi: "রাজশাহী", bogura: "বগুড়া", bogra: "বগুড়া",
  pabna: "পাবনা", sirajganj: "সিরাজগঞ্জ", natore: "নাটোর",
  naogaon: "নওগাঁ", chapai: "চাঁপাইনবাবগঞ্জ", nawabganj: "চাঁপাইনবাবগঞ্জ",
  joypurhat: "জয়পুরহাট",
  kushtia: "কুষ্টিয়া", khulna: "খুলনা", jashore: "যশোর", jessore: "যশোর",
  jhenaidah: "ঝিনাইদহ", chuadanga: "চুয়াডাঙ্গা", meherpur: "মেহেরপুর",
  magura: "মাগুরা", narail: "নড়াইল", satkhira: "সাতক্ষীরা", bagerhat: "বাগেরহাট",
  barishal: "বরিশাল", barisal: "বরিশাল", patuakhali: "পটুয়াখালী",
  bhola: "ভোলা", pirojpur: "পিরোজপুর", barguna: "বরগুনা", jhalokati: "ঝালকাঠি",
  sylhet: "সিলেট", sunamganj: "সুনামগঞ্জ", habiganj: "হবিগঞ্জ", moulvibazar: "মৌলভীবাজার",
  rangpur: "রংপুর", dinajpur: "দিনাজপুর", kurigram: "কুড়িগ্রাম",
  gaibandha: "গাইবান্ধা", nilphamari: "নীলফামারী", lalmonirhat: "লালমনিরহাট",
  thakurgaon: "ঠাকুরগাঁও", panchagarh: "পঞ্চগড়",
  mymensingh: "ময়মনসিংহ", jamalpur: "জামালপুর", netrokona: "নেত্রকোনা", sherpur: "শেরপুর"
};

export function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const toRad = (deg) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

export function findDistrictByCoordinates(lat, lng) {
  let best = BD_DISTRICT_CENTROIDS[0];
  let bestDist = Infinity;
  for (const c of BD_DISTRICT_CENTROIDS) {
    const d = haversineKm(lat, lng, c.lat, c.lng);
    if (d < bestDist) {
      bestDist = d;
      best = c;
    }
  }
  return best;
}

export function matchDistrictFromText(text) {
  if (!text) return null;
  const raw = String(text);
  const bnHit = BD_DISTRICTS.find((d) => raw.includes(d.districtBn));
  if (bnHit) return bnHit.districtBn;
  const lower = raw.toLowerCase();
  for (const [alias, bn] of Object.entries(ENGLISH_DISTRICT_ALIASES)) {
    if (lower.includes(alias)) return bn;
  }
  return null;
}

export function resolveLocationFromCoords(lat, lng, rawText = "") {
  const nearest = findDistrictByCoordinates(lat, lng);
  const districtBn = matchDistrictFromText(rawText) || nearest.districtBn;
  const distObj = BD_DISTRICTS.find((d) => d.districtBn === districtBn) || BD_DISTRICTS[0];
  const matchedUpazila =
    distObj.upazilas.find((u) => String(rawText).toLowerCase().includes(u.toLowerCase())) ||
    distObj.upazilas[0] ||
    districtBn;
  return {
    latitude: lat,
    longitude: lng,
    divisionBn: distObj.divisionBn,
    districtBn: distObj.districtBn,
    upazilaBn: matchedUpazila,
    addressLine: rawText && String(rawText).trim() ? String(rawText).trim() : `${matchedUpazila}, ${distObj.districtBn}, ${distObj.divisionBn}`
  };
}
