/* ==========================================================================
   Codeboek Contrastlezer — kleine verbeteringen voor het lezen
   Alles werkt ook zonder JavaScript; dit maakt het alleen prettiger.
   ========================================================================== */

(function () {
  "use strict";

  var root = document.documentElement;
  var THEME_KEY = "contrastlezer-codeboek-thema";
  var TEXT_KEY = "contrastlezer-codeboek-tekst";

  /* ---------- Onthouden: thema en lettergrootte --------------------------- */

  function veiligLezen(sleutel) {
    try {
      return window.localStorage.getItem(sleutel);
    } catch (fout) {
      return null;
    }
  }

  function veiligSchrijven(sleutel, waarde) {
    try {
      window.localStorage.setItem(sleutel, waarde);
    } catch (fout) {
      /* Privémodus of geen opslag: geen probleem. */
    }
  }

  var bewaardThema = veiligLezen(THEME_KEY);
  if (bewaardThema === "light" || bewaardThema === "dark") {
    root.setAttribute("data-theme", bewaardThema);
  }

  var bewaardeTekst = veiligLezen(TEXT_KEY);
  if (bewaardeTekst === "0" || bewaardeTekst === "1" || bewaardeTekst === "2") {
    root.setAttribute("data-text", bewaardeTekst);
  }

  /* ---------- Thema-knop -------------------------------------------------- */

  var themaKnop = document.getElementById("theme-toggle");

  function tekenThemaKnop() {
    if (!themaKnop) return;
    var licht = root.getAttribute("data-theme") === "light";
    themaKnop.setAttribute("aria-pressed", licht ? "true" : "false");
    themaKnop.setAttribute(
      "aria-label",
      licht ? "Schakel over naar donker thema" : "Schakel over naar licht thema",
    );
    themaKnop.innerHTML = licht
      ? '<span class="tool-icon" aria-hidden="true">☾</span><span class="tool-label">Donker</span>'
      : '<span class="tool-icon" aria-hidden="true">☀</span><span class="tool-label">Licht</span>';
  }

  if (themaKnop) {
    tekenThemaKnop();
    themaKnop.addEventListener("click", function () {
      var licht = root.getAttribute("data-theme") === "light";
      var nieuw = licht ? "dark" : "light";
      root.setAttribute("data-theme", nieuw);
      veiligSchrijven(THEME_KEY, nieuw);
      tekenThemaKnop();
    });
  }

  /* ---------- Lettergrootte --------------------------------------------- */

  var tekstKnoppen = Array.prototype.slice.call(
    document.querySelectorAll("[data-text-size]"),
  );

  function tekenTekstKnoppen() {
    var actief = root.getAttribute("data-text") || "0";
    tekstKnoppen.forEach(function (knop) {
      var isActief = knop.getAttribute("data-text-size") === actief;
      knop.setAttribute("aria-pressed", isActief ? "true" : "false");
    });
  }

  tekstKnoppen.forEach(function (knop) {
    knop.addEventListener("click", function () {
      var maat = knop.getAttribute("data-text-size") || "0";
      root.setAttribute("data-text", maat);
      veiligSchrijven(TEXT_KEY, maat);
      tekenTekstKnoppen();
    });
  });

  tekenTekstKnoppen();

  /* ---------- Leesvoortgang --------------------------------------------- */

  var voortgang = document.getElementById("progress-bar");

  function werkVoortgangBij() {
    if (!voortgang) return;
    var doc = document.documentElement;
    var ruimte = doc.scrollHeight - doc.clientHeight;
    var deel = ruimte > 0 ? doc.scrollTop / ruimte : 0;
    voortgang.style.width = Math.min(100, Math.max(0, deel * 100)) + "%";
  }

  /* ---------- Terug naar boven ------------------------------------------ */

  var naarBoven = document.getElementById("to-top");

  function werkNaarBovenBij() {
    if (!naarBoven) return;
    naarBoven.classList.toggle("is-visible", window.scrollY > 650);
  }

  if (naarBoven) {
    naarBoven.addEventListener("click", function () {
      var rustig = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      window.scrollTo({ top: 0, behavior: rustig ? "auto" : "smooth" });
    });
  }

  /* ---------- Actief hoofdstuk in de inhoudsopgave --------------------- */

  var inhoudLinks = Array.prototype.slice.call(
    document.querySelectorAll(".toc a[href^='#']"),
  );

  var hoofdstukken = inhoudLinks
    .map(function (link) {
      var id = link.getAttribute("href").slice(1);
      return document.getElementById(id);
    })
    .filter(Boolean);

  function zetActief(id) {
    inhoudLinks.forEach(function (link) {
      var isActief = link.getAttribute("href") === "#" + id;
      link.classList.toggle("is-active", isActief);
      if (isActief) {
        link.setAttribute("aria-current", "location");
      } else {
        link.removeAttribute("aria-current");
      }
    });
  }

  if ("IntersectionObserver" in window && hoofdstukken.length > 0) {
    var zichtbaar = new Map();

    var waarnemer = new IntersectionObserver(
      function (meldingen) {
        meldingen.forEach(function (melding) {
          zichtbaar.set(melding.target.id, melding.isIntersecting ? melding.intersectionRatio : 0);
        });

        var besteId = null;
        var besteWaarde = -1;
        zichtbaar.forEach(function (waarde, id) {
          if (waarde > besteWaarde) {
            besteWaarde = waarde;
            besteId = id;
          }
        });

        if (besteId && besteWaarde > 0) {
          zetActief(besteId);
        }
      },
      {
        rootMargin: "-15% 0px -60% 0px",
        threshold: [0, 0.15, 0.4, 0.75],
      },
    );

    hoofdstukken.forEach(function (hoofdstuk) {
      waarnemer.observe(hoofdstuk);
    });
  }

  /* ---------- Zoeken in de inhoudsopgave -------------------------------- */

  var zoekveld = document.getElementById("toc-search");
  var zoekUitkomst = document.getElementById("search-count");
  var zoekCache = new Map();

  function tekstVanHoofdstuk(id) {
    if (zoekCache.has(id)) return zoekCache.get(id);
    var hoofdstuk = document.getElementById(id);
    var tekst = hoofdstuk ? hoofdstuk.textContent.toLowerCase() : "";
    zoekCache.set(id, tekst);
    return tekst;
  }

  function filterInhoud() {
    if (!zoekveld) return;
    var query = zoekveld.value.trim().toLowerCase();
    var gevonden = 0;

    inhoudLinks.forEach(function (link) {
      var id = link.getAttribute("href").slice(1);
      var titel = link.textContent.toLowerCase();
      var past = query === "" || titel.indexOf(query) !== -1 || tekstVanHoofdstuk(id).indexOf(query) !== -1;
      var lijstItem = link.closest("li");

      if (lijstItem) lijstItem.classList.toggle("is-hidden", !past);
      if (past) gevonden += 1;
    });

    if (zoekUitkomst) {
      if (query === "") {
        zoekUitkomst.textContent = "";
      } else if (gevonden === 1) {
        zoekUitkomst.textContent = "1 hoofdstuk gevonden";
      } else {
        zoekUitkomst.textContent = gevonden + " hoofdstukken gevonden";
      }
    }
  }

  if (zoekveld) {
    zoekveld.addEventListener("input", filterInhoud);
    zoekveld.addEventListener("keydown", function (gebeurtenis) {
      if (gebeurtenis.key === "Escape") {
        zoekveld.value = "";
        filterInhoud();
        zoekveld.blur();
        return;
      }

      if (gebeurtenis.key === "Enter") {
        var eerste = inhoudLinks.find(function (link) {
          var item = link.closest("li");
          return item && !item.classList.contains("is-hidden");
        });
        if (eerste) {
          gebeurtenis.preventDefault();
          eerste.click();
        }
      }
    });
  }

  document.addEventListener("keydown", function (gebeurtenis) {
    var doel = gebeurtenis.target;
    var inVeld =
      doel &&
      (doel.tagName === "INPUT" || doel.tagName === "TEXTAREA" || doel.isContentEditable);

    if (gebeurtenis.key === "/" && !inVeld && zoekveld) {
      gebeurtenis.preventDefault();
      zoekveld.focus();
    }
  });

  /* ---------- Code kopiëren --------------------------------------------- */

  var kopieerKnoppen = Array.prototype.slice.call(
    document.querySelectorAll(".copy-btn"),
  );

  function kopieerTekst(tekst) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(tekst);
    }

    return new Promise(function (resolve, reject) {
      var veld = document.createElement("textarea");
      veld.value = tekst;
      veld.setAttribute("readonly", "");
      veld.style.position = "fixed";
      veld.style.opacity = "0";
      document.body.appendChild(veld);
      veld.select();
      try {
        var gelukt = document.execCommand("copy");
        document.body.removeChild(veld);
        gelukt ? resolve() : reject(new Error("Kopiëren mislukt"));
      } catch (fout) {
        document.body.removeChild(veld);
        reject(fout);
      }
    });
  }

  kopieerKnoppen.forEach(function (knop) {
    knop.addEventListener("click", function () {
      var blok = knop.closest(".codeblock");
      var code = blok ? blok.querySelector("code") : null;
      if (!code) return;

      kopieerTekst(code.textContent).then(
        function () {
          knop.textContent = "Gekopieerd!";
          knop.classList.add("is-done");
          window.setTimeout(function () {
            knop.textContent = "Kopieer";
            knop.classList.remove("is-done");
          }, 1600);
        },
        function () {
          knop.textContent = "Mislukt";
          window.setTimeout(function () {
            knop.textContent = "Kopieer";
          }, 1600);
        },
      );
    });
  });

  /* ---------- Scroll-luisteraar ----------------------------------------- */

  var wachtOpFrame = null;

  function opScroll() {
    if (wachtOpFrame !== null) return;
    wachtOpFrame = window.requestAnimationFrame(function () {
      werkVoortgangBij();
      werkNaarBovenBij();
      wachtOpFrame = null;
    });
  }

  window.addEventListener("scroll", opScroll, { passive: true });
  window.addEventListener("resize", opScroll);
  werkVoortgangBij();
  werkNaarBovenBij();

  /* ---------- Jaartal ---------------------------------------------------- */

  var jaar = document.getElementById("jaar");
  if (jaar) {
    jaar.textContent = String(new Date().getFullYear());
  }
})();
