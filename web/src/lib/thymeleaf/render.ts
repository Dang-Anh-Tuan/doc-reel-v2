/**
 * Render template Thymeleaf trong trình duyệt — chỉ dùng cho preview (server vẫn render bằng Thymeleaf thật).
 *
 * Chỉ hỗ trợ đúng tập con template DocReel được phép dùng (SPEC mục 7). Thuộc tính `th:*` khác
 * (vd `th:utext`) → cảnh báo trong `warnings`, không render.
 */
import { type Context, evaluate, evaluateAny, isTruthy, splitTopLevel, toText } from "./expression";

export const ALLOWED_ATTRIBUTES = new Set([
  "text",
  "each",
  "if",
  "unless",
  "classappend",
  "attr",
  "href",
  "src",
  "replace",
  "insert",
  "fragment",
  "remove",
]);

export interface TemplateModel {
  video: unknown;
  scene: unknown;
  index: number | null;
  total: number | null;
  frame: number | null;
}

export interface RenderResult {
  html: string;
  warnings: string[];
}

export interface RenderOptions {
  /** Gắn `<base href>` để srcdoc / blob URL tải được /css, /js... */
  baseHref?: string;
  /** Tiền tố cho link tuyệt đối `@{/...}` (context path của Spring). */
  contextPath?: string;
}

interface Env {
  fragments: Map<string, Element>;
  warnings: string[];
  contextPath: string;
}

function describe(el: Element): string {
  const cls = el.getAttribute("class")?.split(/\s+/)[0];
  return `<${el.tagName.toLowerCase()}${cls ? "." + cls : ""}>`;
}

function processElement(el: Element, ctx: Context, env: Env): void {
  const th = (name: string) => el.getAttribute(`th:${name}`);
  const attempt = <T>(label: string, fn: () => T, fallback: T): T => {
    try {
      return fn();
    } catch (err) {
      env.warnings.push(`${label} ${describe(el)}: ${(err as Error).message}`);
      return fallback;
    }
  };

  if (th("remove") === "all") {
    el.remove();
    return;
  }

  // 1. Fragment
  const replace = th("replace");
  const insert = th("insert");
  const fragmentRef = replace ?? insert;
  if (fragmentRef != null) {
    const name = /::\s*([\w-]+)/.exec(fragmentRef)?.[1];
    const fragment = name ? env.fragments.get(name) : undefined;
    if (!fragment) {
      env.warnings.push(`Không tìm thấy fragment ${fragmentRef}`);
      el.removeAttribute("th:replace");
      el.removeAttribute("th:insert");
    } else {
      const clone = el.ownerDocument.importNode(fragment, true);
      clone.removeAttribute("th:fragment");
      if (replace != null) {
        el.replaceWith(clone);
        processElement(clone, ctx, env);
        return;
      }
      el.removeAttribute("th:insert");
      el.replaceChildren(clone);
    }
  }

  // 2. th:each
  const each = th("each");
  if (each != null) {
    el.removeAttribute("th:each");
    const match = /^\s*(\w+)\s*(?:,\s*(\w+))?\s*:\s*([\s\S]+)$/.exec(each);
    if (!match) {
      env.warnings.push(`th:each sai cú pháp: ${each}`);
      el.remove();
      return;
    }
    const itemVar = match[1]!;
    const statVar = match[2] ?? `${itemVar}Stat`;
    const source = attempt("th:each", () => evaluate(match[3]!, ctx), [] as unknown);
    const items = Array.isArray(source) ? source : source == null ? [] : [source];
    const parent = el.parentNode;
    items.forEach((item, index) => {
      const clone = el.cloneNode(true) as Element;
      parent?.insertBefore(clone, el);
      const stat = {
        index,
        count: index + 1,
        size: items.length,
        current: item,
        first: index === 0,
        last: index === items.length - 1,
        even: (index + 1) % 2 === 0,
        odd: (index + 1) % 2 === 1,
      };
      processElement(clone, { ...ctx, [itemVar]: item, [statVar]: stat }, env);
    });
    el.remove();
    return;
  }

  // 3. Điều kiện
  const cond = th("if");
  if (cond != null && !isTruthy(attempt("th:if", () => evaluate(cond, ctx), null))) {
    el.remove();
    return;
  }
  const unless = th("unless");
  if (unless != null && isTruthy(attempt("th:unless", () => evaluate(unless, ctx), null))) {
    el.remove();
    return;
  }

  // 4. Thuộc tính
  const setOrRemove = (name: string, value: unknown) => {
    if (value == null) el.removeAttribute(name);
    else el.setAttribute(name, toText(value));
  };
  const attr = th("attr");
  if (attr != null) {
    for (const part of splitTopLevel(attr, ",")) {
      const eq = part.indexOf("=");
      const name = part.slice(0, eq).trim();
      setOrRemove(name, attempt("th:attr", () => evaluateAny(part.slice(eq + 1), ctx, env.contextPath), null));
    }
  }
  for (const name of ["href", "src"]) {
    const expr = th(name);
    if (expr != null) setOrRemove(name, attempt(`th:${name}`, () => evaluateAny(expr, ctx, env.contextPath), null));
  }
  const classappend = th("classappend");
  if (classappend != null) {
    const value = attempt("th:classappend", () => evaluateAny(classappend, ctx, env.contextPath), null);
    if (value) el.classList.add(...toText(value).split(/\s+/).filter(Boolean));
  }

  // 5. Nội dung
  const text = th("text");
  if (text != null) el.textContent = toText(attempt("th:text", () => evaluate(text, ctx), ""));

  for (const name of el.getAttributeNames()) {
    if (!name.startsWith("th:")) continue;
    if (!ALLOWED_ATTRIBUTES.has(name.slice(3))) {
      env.warnings.push(`Thuộc tính không được phép: ${name} trên ${describe(el)}`);
    }
    el.removeAttribute(name);
  }
  for (const child of Array.from(el.children)) processElement(child, ctx, env);
}

/** Render một template (chuỗi HTML) với layout chứa các `th:fragment`. */
export function renderTemplate(
  templateHtml: string,
  layoutHtml: string,
  model: TemplateModel,
  { baseHref, contextPath = "" }: RenderOptions = {},
): RenderResult {
  const parser = new DOMParser();
  const doc = parser.parseFromString(templateHtml, "text/html");
  const layout = parser.parseFromString(layoutHtml, "text/html");

  const fragments = new Map<string, Element>();
  for (const el of Array.from(layout.querySelectorAll("*"))) {
    const name = el.getAttribute("th:fragment");
    if (name) fragments.set(name.split("(")[0]!.trim(), el);
  }

  const env: Env = { fragments, warnings: [], contextPath };
  const ctx: Context = { ...model };
  processElement(doc.documentElement, ctx, env);
  doc.documentElement.removeAttribute("xmlns:th");

  if (baseHref) {
    const base = doc.createElement("base");
    base.href = baseHref;
    doc.head.prepend(base);
  }
  return { html: `<!DOCTYPE html>\n${doc.documentElement.outerHTML}`, warnings: env.warnings };
}
