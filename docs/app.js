(function () {
  const REPO_API = "https://api.github.com/repos/gianrufin/Kalye/releases/latest";
  const FALLBACK_URL = "https://github.com/gianrufin/Kalye/releases/latest";

  // 1. Theme Persistence & Toggle
  const root = document.documentElement;
  const themeToggle = document.getElementById("themeToggle");
  const savedTheme = localStorage.getItem("kalye_theme");
  if (savedTheme === "dark" || savedTheme === "light") {
    root.setAttribute("data-theme", savedTheme);
  }
  if (themeToggle) {
    themeToggle.addEventListener("click", function () {
      const next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      localStorage.setItem("kalye_theme", next);
    });
  }

  // 2. Mobile Navigation Drawer
  const menuBtn = document.getElementById("mobileMenuBtn");
  const drawer = document.getElementById("mobileDrawer");
  if (menuBtn && drawer) {
    menuBtn.addEventListener("click", function () {
      const open = drawer.hasAttribute("hidden");
      if (open) {
        drawer.removeAttribute("hidden");
        menuBtn.setAttribute("aria-expanded", "true");
      } else {
        drawer.setAttribute("hidden", "");
        menuBtn.setAttribute("aria-expanded", "false");
      }
    });
    drawer.querySelectorAll("a").forEach(function (link) {
      link.addEventListener("click", function () {
        drawer.setAttribute("hidden", "");
        menuBtn.setAttribute("aria-expanded", "false");
      });
    });
  }

  // 3. Staggered Scroll Entrance Animations
  const revealEls = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window) {
    const io = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -30px 0px" }
    );
    revealEls.forEach(function (el) {
      io.observe(el);
    });
  } else {
    revealEls.forEach(function (el) {
      el.classList.add("is-visible");
    });
  }

  // 4. Interactive Screen Explorer
  const SCREENS = {
    track_live: {
      src: "assets/screenshots/track_live.png",
      tag: "01 / LIVE TRACKING COCKPIT",
      title: "Zero-flicker cockpit over minimal street maps",
      desc: "Designed to run cleanly alongside Google Maps or Android Auto without clutter. Features an isolated 1-second moving timer, 25-second stationary traffic auto-pause, OLED Stealth Battery-Saver view, and one-tap Fuel, Ferry, Camp, and Stop-Over logging.",
      bullets: [
        "Free OSM-backed muted street canvas (zero API key required)",
        "MSL geoid + barometer pressure fusion for accurate climb tracking",
        "One-handed thumb-zone Pause & Finish controls"
      ]
    },
    garage: {
      src: "assets/screenshots/garage.png",
      tag: "02 / VEHICLE GARAGE & AVATAR STUDIO",
      title: "Custom rigs across 8 body styles & per-rig odometers",
      desc: "Personalize your fleet with 4x4 SUVs, Crew-Cab Pickups, Crossovers, Sedans, Hatchbacks, Coupes, Vans, and Motorcycles—each with custom paint swatches, Expedition gear, live fuel efficiency averages, and your own Rig Avatar badge.",
      bullets: [
        "Live 2.2:1 custom rig preview studio when adding or editing vehicles",
        "Automatic per-rig trip count, odometer accumulation & avg km/L",
        "Customize compass-ring & scenic Rig Avatar badges"
      ]
    },
    trip_detail: {
      src: "assets/screenshots/trip_detail.png",
      tag: "03 / TRIP DETAIL & ALTITUDE PROFILE",
      title: "Interactive elevation curve & full journey cost ledger",
      desc: "Inspect every recorded pass with a smooth route preview, scrubbed climb/descent altitude profile, peak elevation, max speed, itemized fuel/ferry/camp costs, and instant Share Card, GPX 1.1, and CSV exports.",
      bullets: [
        "Touch-scrubbing elevation graph with min/peak metrics",
        "Clean cost breakdown by Fuel liters, Ferry, and Camp stops",
        "One-tap Share Card Studio, GPX 1.1 XML, and Excel CSV export"
      ]
    },
    share_card: {
      src: "assets/screenshots/share_card.png",
      tag: "04 / ROADMAP & PNG SHARE STUDIO",
      title: "Travel Roadmap cards & transparent PNG stickers",
      desc: "Switch between a full-route Travel Roadmap map card (Light Atlas, Slate Mono, or Night Map) and transparent 1080×1350 PNG overlays saved with unique yyyyMMdd_HHmmss.png filenames.",
      bullets: [
        "Switch between Sticker Overlay and full-route Travel Roadmap card",
        "Shows Start/Finish markers, stop pins & clean summary HUD",
        "Live preview over your overland backdrop, map theme, or alpha grid"
      ]
    },
    trips_history: {
      src: "assets/screenshots/trips_history.png",
      tag: "05 / EXPEDITION LOG & RIG FILTERING",
      title: "Filter your logbook by terrain tag or vehicle rig",
      desc: "Browse every past drive with miniature route trail previews, category tag pills, vehicle rig filters, Cloud Firestore sync badges, and full CSV spreadsheet export.",
      bullets: [
        "Filter rides instantly by Vehicle Rig or Custom Tag Category",
        "Offline SQLite persistence backed by Google Sign-In cloud sync",
        "Export your entire expedition ledger to CSV in one tap"
      ]
    }
  };

  const tabBtns = document.querySelectorAll(".screen-tab");
  const thumbBtns = document.querySelectorAll(".thumb-card");
  const activeImg = document.getElementById("activeScreenImg");
  const screenTag = document.getElementById("screenTag");
  const screenTitle = document.getElementById("screenTitle");
  const screenDesc = document.getElementById("screenDesc");
  const screenBullets = document.getElementById("screenBullets");
  const activeFrame = document.getElementById("activeScreenFrame");

  let currentScreenKey = "track_live";

  function selectScreen(key) {
    const item = SCREENS[key];
    if (!item || key === currentScreenKey) return;
    currentScreenKey = key;

    tabBtns.forEach(function (btn) {
      const match = btn.getAttribute("data-screen") === key;
      btn.classList.toggle("is-active", match);
      btn.setAttribute("aria-selected", match ? "true" : "false");
    });

    thumbBtns.forEach(function (btn) {
      btn.classList.toggle("is-active", btn.getAttribute("data-screen") === key);
    });

    if (activeImg) {
      activeImg.style.opacity = "0.25";
      setTimeout(function () {
        activeImg.src = item.src;
        activeImg.alt = item.title;
        activeImg.style.opacity = "1";
      }, 120);
    }
    if (screenTag) screenTag.textContent = item.tag;
    if (screenTitle) screenTitle.textContent = item.title;
    if (screenDesc) screenDesc.textContent = item.desc;
    if (screenBullets) {
      screenBullets.innerHTML = item.bullets
        .map(function (b) {
          return "<li>" + b + "</li>";
        })
        .join("");
    }
  }

  tabBtns.forEach(function (btn) {
    btn.addEventListener("click", function () {
      selectScreen(btn.getAttribute("data-screen"));
    });
  });

  thumbBtns.forEach(function (btn) {
    btn.addEventListener("click", function () {
      selectScreen(btn.getAttribute("data-screen"));
    });
  });

  // 5. Lightbox Zoom Viewer
  const lightbox = document.getElementById("lightbox");
  const lightboxImg = document.getElementById("lightboxImg");
  const lightboxCaption = document.getElementById("lightboxCaption");
  const lightboxClose = document.getElementById("lightboxClose");

  function openLightbox() {
    const item = SCREENS[currentScreenKey];
    if (!lightbox || !lightboxImg || !item) return;
    lightboxImg.src = item.src;
    lightboxImg.alt = item.title;
    if (lightboxCaption) lightboxCaption.textContent = item.title;
    lightbox.removeAttribute("hidden");
  }

  function closeLightbox() {
    if (lightbox) lightbox.setAttribute("hidden", "");
  }

  if (activeFrame) {
    activeFrame.addEventListener("click", openLightbox);
    activeFrame.addEventListener("keydown", function (e) {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        openLightbox();
      }
    });
  }
  if (lightboxClose) lightboxClose.addEventListener("click", closeLightbox);
  if (lightbox) {
    lightbox.addEventListener("click", function (e) {
      if (e.target === lightbox) closeLightbox();
    });
  }
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") closeLightbox();
  });

  // 6. Live GitHub Release APK Link Resolver
  fetch(REPO_API, { headers: { Accept: "application/vnd.github+json" } })
    .then(function (res) {
      if (!res.ok) throw new Error("GitHub API status " + res.status);
      return res.json();
    })
    .then(function (release) {
      if (!release) return;
      const version = release.tag_name || "v1.0.3";
      const assets = Array.isArray(release.assets) ? release.assets : [];
      const apkAsset = assets.find(function (a) {
        return a && typeof a.name === "string" && a.name.toLowerCase().endsWith(".apk");
      });
      const apkUrl = (apkAsset && apkAsset.browser_download_url) || release.html_url || FALLBACK_URL;

      document.querySelectorAll(".js-apk-download").forEach(function (a) {
        a.href = apkUrl;
      });
      document.querySelectorAll(".js-version-badge").forEach(function (el) {
        el.textContent = version;
      });
      document.querySelectorAll(".js-apk-filename").forEach(function (el) {
        if (apkAsset && apkAsset.name) el.textContent = apkAsset.name;
      });
      if (apkAsset && apkAsset.size > 0) {
        const mb = (apkAsset.size / (1024 * 1024)).toFixed(1) + " MB";
        document.querySelectorAll(".js-apk-meta").forEach(function (el) {
          el.textContent = version + " • " + mb;
        });
      }
    })
    .catch(function () {
      // Keep clean fallback link to /releases/latest
    });
})();
