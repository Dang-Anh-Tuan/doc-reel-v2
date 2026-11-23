import { readFileSync, readdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { SCENE_TYPES } from "@/domain/script";
import { type TemplateModel, renderTemplate } from "./render";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../../../..");
const repo = (path: string) => resolve(repoRoot, path);
const read = (path: string) => readFileSync(repo(path), "utf8");

const layout = read("src/main/resources/templates/layout.html");
const template = (type: string) => read(`src/main/resources/templates/scenes/${type}.html`);

function render(type: string, model: Partial<TemplateModel>) {
  const full: TemplateModel = { video: null, scene: null, index: 1, total: 10, frame: null, ...model };
  const result = renderTemplate(template(type), layout, full);
  const doc = new DOMParser().parseFromString(result.html, "text/html");
  return { ...result, doc };
}

describe("bộ render với template thật", () => {
  it("có đủ 10 template", () => {
    const files = readdirSync(repo("src/main/resources/templates/scenes")).map((f) => f.replace(/\.html$/, ""));
    expect(files.sort()).toEqual([...SCENE_TYPES].sort());
  });

  for (const type of SCENE_TYPES) {
    it(`${type}: render mẫu ok không cảnh báo, không còn th:*`, () => {
      const sample = JSON.parse(read(`samples/scenes/${type}.ok.json`)) as Pick<TemplateModel, "video" | "scene">;
      const { html, warnings, doc } = render(type, { ...sample, frame: type === "quiz" ? 1 : null });

      expect(warnings).toEqual([]);
      expect(html).not.toMatch(/\sth:[a-z]+=/);
      expect(doc.querySelector(".stage")).not.toBeNull();
      expect(doc.querySelector(".track-chip")?.textContent).toBe(sample.video && (sample.video as { track: string }).track);
      expect(doc.querySelector("link#theme-css")?.getAttribute("href")).toBe("/css/themes/midnight-brass.css");
    });
  }
});

describe("escape + logic template", () => {
  const video = { topicId: "T-01", track: "Test", title: "t", theme: "petrol-sage" };

  it("chữ trong JSON luôn được escape (không có th:utext)", () => {
    const scene = { accent: "<b>x</b>", titleRest: "&amp;", hook: "<script>alert(1)</script>", chip: "c" };
    const { html, doc } = render("title", { video, scene });
    expect(html).not.toContain("<script>alert");
    expect(doc.querySelector(".hook")?.textContent).toBe("<script>alert(1)</script>");
    expect(doc.querySelector("link#theme-css")?.getAttribute("href")).toBe("/css/themes/petrol-sage.css");
  });

  it("points: lặp theo số ý, bỏ dòng mẫu tĩnh, đánh số 01..", () => {
    const scene = { accent: "A", points: [{ heading: "h1", detail: "d1" }, { heading: "h2", detail: "d2" }, { heading: "h3", detail: "d3" }] };
    const { doc } = render("points", { video, scene });
    const nums = Array.from(doc.querySelectorAll(".num")).map((n) => n.textContent);
    expect(nums).toEqual(["01", "02", "03"]);
  });

  it("quiz: đánh dấu đáp án đúng theo index từ 0, ghi data-frame", () => {
    const scene = { question: "q", options: ["a", "b", "c"], answer: 2, explain: "e" };
    const { doc } = render("quiz", { video, scene, frame: 2 });
    const options = Array.from(doc.querySelectorAll(".opt"));
    expect(options.map((o) => o.querySelector(".opt-letter")?.textContent)).toEqual(["A", "B", "C"]);
    expect(options.map((o) => o.classList.contains("is-answer"))).toEqual([false, false, true]);
    expect(doc.body.dataset.frame).toBe("2");
    expect(doc.querySelector(".explain .note-label")?.textContent?.trim()).toBe("Đáp án C");
  });

  it("code: noteKind mặc định info, highlight thành data-highlight", () => {
    const scene = { accent: "A", lang: "java", code: "a\nb", highlight: [2], note: "n" };
    const { doc } = render("code", { video, scene });
    expect(doc.querySelector("aside")?.className).toContain("note--info");
    expect(doc.querySelector("pre")?.getAttribute("data-highlight")).toBe("[2]");
    expect(doc.querySelector("pre")?.getAttribute("data-lang")).toBe("java");
  });

  it("bỏ phần tử khi th:if sai (note không có)", () => {
    const scene = { accent: "A", lang: "java", code: "a" };
    const { doc } = render("code", { video, scene });
    expect(doc.querySelector("aside.note")).toBeNull();
  });

  it("cảnh báo thuộc tính không được phép", () => {
    const tpl = '<html xmlns:th="http://www.thymeleaf.org"><body><p th:utext="${x}">x</p></body></html>';
    const { warnings } = renderTemplate(tpl, layout, { video, scene: {}, index: 1, total: 1, frame: null });
    expect(warnings.some((w) => w.includes("th:utext"))).toBe(true);
  });
});
