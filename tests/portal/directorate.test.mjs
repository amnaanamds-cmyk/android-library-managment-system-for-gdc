// tests/portal/directorate.test.mjs
//
// Drives the directorate portal in a real browser against the Firebase
// emulators. This is the only test that proves the console actually works —
// that the filters filter, that a pending college is listed rather than only
// queued, and that a row links through to its college.
//
// Run:
//   firebase emulators:start --only auth,firestore
//   node tests/portal/seed.mjs
//   cd web-app && NEXT_PUBLIC_FIREBASE_EMULATOR=1 npm run build && \
//     NEXT_PUBLIC_FIREBASE_EMULATOR=1 npx next start -p 3111
//   node tests/portal/directorate.test.mjs
//
// BASE_URL overrides the port. SCREENSHOT_DIR, if set, receives a full-page
// capture of the college detail page.
import { chromium } from "playwright";
const OUT = process.env.SCREENSHOT_DIR || "";
const BASE = process.env.BASE_URL || "http://localhost:3111";
let pass = 0, fail = 0;
const check = (label, cond, detail = "") => {
  if (cond) { console.log("  ok  ", label); pass++; }
  else { console.log("  FAIL", label, detail); fail++; }
};

const browser = await chromium.launch(
  process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {},
);
const ctx = await browser.newContext({ viewport: { width: 1500, height: 1100 } });
const page = await ctx.newPage();
await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
await page.waitForTimeout(2000);
await page.fill('input[type="email"]', "directorate.officer@hed.gkp.pk");
await page.fill('input[type="password"]', "Passw0rd!");
await page.click("#auth-submit-btn");
await page.waitForTimeout(4000);
await page.goto(`${BASE}/director`, { waitUntil: "domcontentloaded" });
await page.waitForTimeout(3000);

const rows = () => page.locator("table").last().locator("tbody tr").count();
const names = async () =>
  (await page.locator("table").last().locator("tbody tr td:first-child").allTextContents())
    .map((t) => t.trim().split("\n")[0]);

console.log("\n-- Directorate console --");
check("every registered college is listed, pending ones included",
      (await rows()) === 8, `saw ${await rows()}`);

await page.getByRole("button", { name: /^Suspended/ }).click();
await page.waitForTimeout(400);
check("Status → Suspended shows only the suspended college", (await rows()) === 1, JSON.stringify(await names()));
check("and it is the right one", (await names())[0].includes("Dera Ismail Khan"));

await page.getByRole("button", { name: /^All/ }).first().click();
await page.waitForTimeout(300);
await page.getByRole("button", { name: /^Never/ }).click();
await page.waitForTimeout(400);
// Two: an approved college that has never synced, and the pending one, which
// by definition has not reported either.
check("Reporting → Never finds both colleges that have not synced",
      (await rows()) === 2, JSON.stringify(await names()));
check("and they are Bannu and Chitral",
      (await names()).join("|").includes("Bannu") && (await names()).join("|").includes("Chitral"));

await page.getByRole("button", { name: /^Stale/ }).click();
await page.waitForTimeout(400);
check("Reporting → Stale finds the two quiet colleges", (await rows()) === 2, JSON.stringify(await names()));

await page.getByRole("button", { name: "clear filters" }).click();
await page.waitForTimeout(400);
check("clear filters restores every row", (await rows()) === 8, `saw ${await rows()}`);

await page.selectOption("select", "Mardan");
await page.waitForTimeout(400);
check("District → Mardan narrows to one college", (await rows()) === 1, JSON.stringify(await names()));

await page.getByRole("button", { name: "clear filters" }).click();
await page.waitForTimeout(300);
await page.fill('input[placeholder*="Search"]', "peshawar");
await page.waitForTimeout(500);
check("search matches on name", (await rows()) === 1, JSON.stringify(await names()));

await page.fill('input[placeholder*="Search"]', "zzzz-no-such-college");
await page.waitForTimeout(500);
check("an empty result explains itself",
      (await page.locator("text=No college matches these filters").count()) === 1);

await page.getByRole("button", { name: "Show all colleges" }).click();
await page.waitForTimeout(400);

await page.getByRole("button", { name: /^Pending/ }).click();
await page.waitForTimeout(400);
check("Status → Pending now matches the college awaiting approval",
      (await rows()) === 1, JSON.stringify(await names()));
check("and it is GDC Chitral", (await names())[0].includes("Chitral"));
await page.getByRole("button", { name: "clear filters" }).click();
await page.waitForTimeout(400);

// Drill-down
await page.locator("table").last().locator("tbody tr a").first().click();
await page.waitForTimeout(3000);
check("a row links through to that college's detail page", /\/director\/GDC-/.test(page.url()), page.url());
if (OUT) await page.screenshot({ path: `${OUT}/director-detail.png`, fullPage: true });

console.log(`\n${pass} passed, ${fail} failed`);
await browser.close();
process.exit(fail === 0 ? 0 : 1);
