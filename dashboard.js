/* Informativa generale: evidenzia nell'indice la sezione che si sta leggendo, la porta in vista nella barra
   e mostra il pulsante "torna su". Nessun dato esce dalla pagina. */
(function () {
  var toc = document.querySelector("nav.toc"); if (!toc) return;
  var links = [].slice.call(toc.querySelectorAll('a[href^="#"]')), byId = {};
  links.forEach(function (a) { byId[a.getAttribute("href").slice(1)] = a; });
  var current = null;
  function mark(id) {
    var a = byId[id]; if (!a || a === current) return;
    if (current) current.classList.remove("on");
    a.classList.add("on"); current = a;
    if (toc.scrollWidth > toc.clientWidth) toc.scrollTo({ left: a.offsetLeft - toc.clientWidth / 2 + a.offsetWidth / 2, behavior: "smooth" });
  }
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) { if (e.isIntersecting) mark(e.target.id); });
    }, { rootMargin: "-80px 0px -65% 0px" });
    Object.keys(byId).forEach(function (id) { var s = document.getElementById(id); if (s) io.observe(s); });
  }
  var btn = document.createElement("button");
  btn.className = "to-top"; btn.type = "button"; btn.textContent = "↑"; btn.setAttribute("aria-label", "Torna su");
  btn.addEventListener("click", function () { window.scrollTo({ top: 0, behavior: "smooth" }); });
  document.body.appendChild(btn);
  window.addEventListener("scroll", function () { btn.classList.toggle("on", window.scrollY > 600); }, { passive: true });
})();
