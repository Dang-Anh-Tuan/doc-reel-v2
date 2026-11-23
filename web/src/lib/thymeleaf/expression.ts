/**
 * Dịch & tính biểu thức Thymeleaf/SpEL đơn giản trong trình duyệt.
 *
 * Hỗ trợ: `${...}` (truy cập thuộc tính, gọi method như `'ABCD'.charAt(i)`, ==, !=, <, >, +,
 * `a ? b : c`, Elvis `a ?: b`, and/or/not, eq/ne/gt/lt/ge/le), chuỗi `'...'`,
 * link `@{/path/{v}.css(v=${...}, q=...)}`.
 * Không hỗ trợ utility object (`#lists`, `#strings`...) → ném lỗi để template không dùng.
 */

export type Context = Record<string, unknown>;

const WORD_OPS: Record<string, string> = {
  and: "&&",
  or: "||",
  not: "!",
  eq: "==",
  ne: "!=",
  gt: ">",
  lt: "<",
  ge: ">=",
  le: "<=",
};

const isIdentChar = (c: string | undefined) => c !== undefined && /[\w$#.]/.test(c);

/** Vị trí ký tự đóng tương ứng với ký tự mở tại `openIdx` (bỏ qua trong chuỗi '...'). */
export function findClose(src: string, openIdx: number, open: string, close: string): number {
  let depth = 0;
  let quoted = false;
  for (let i = openIdx; i < src.length; i++) {
    const c = src[i];
    if (c === "'") quoted = !quoted;
    if (quoted) continue;
    if (c === open) depth++;
    else if (c === close && --depth === 0) return i;
  }
  throw new Error(`Thiếu '${close}' trong: ${src}`);
}

/** Tách chuỗi theo `separator` ở mức ngoài cùng (không tách trong ngoặc hoặc chuỗi). */
export function splitTopLevel(src: string, separator: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let quoted = false;
  let current = "";
  for (const c of src) {
    if (c === "'") quoted = !quoted;
    if (!quoted) {
      if ("({[".includes(c)) depth++;
      if (")}]".includes(c)) depth--;
      if (c === separator && depth === 0) {
        parts.push(current);
        current = "";
        continue;
      }
    }
    current += c;
  }
  if (current.trim()) parts.push(current);
  return parts;
}

/** Dịch biểu thức Thymeleaf sang biểu thức JS tương đương. */
export function translate(src: string): string {
  let out = "";
  let i = 0;
  while (i < src.length) {
    const c = src[i]!;

    if (c === "'") {
      let j = i + 1;
      let literal = "";
      while (j < src.length && src[j] !== "'") {
        if (src[j] === "\\" && j + 1 < src.length) {
          literal += src[j + 1];
          j += 2;
          continue;
        }
        literal += src[j++];
      }
      out += JSON.stringify(literal);
      i = j + 1;
      continue;
    }

    if (c === "$" && src[i + 1] === "{") {
      const end = findClose(src, i + 1, "{", "}");
      out += `(${translate(src.slice(i + 2, end))})`;
      i = end + 1;
      continue;
    }

    if (c === "#" && /[a-z]/.test(src[i + 1] ?? "")) {
      throw new Error(`Không hỗ trợ utility object Thymeleaf: ${src.slice(i, i + 20)}…`);
    }

    if (c === "?" && src[i + 1] === ":") {
      out += "??";
      i += 2;
      continue;
    }

    const word = /^[a-z]+\b/.exec(src.slice(i))?.[0];
    if (word && WORD_OPS[word] && !isIdentChar(src[i - 1])) {
      out += ` ${WORD_OPS[word]} `;
      i += word.length;
      continue;
    }

    out += c;
    i++;
  }
  return out;
}

const compiled = new Map<string, (ctx: Context) => unknown>();

/** Tính biểu thức Thymeleaf với context. Ném lỗi nếu cú pháp sai hoặc truy cập thuộc tính của null. */
export function evaluate(expr: string, ctx: Context): unknown {
  const key = expr.trim();
  let fn = compiled.get(key);
  if (!fn) {
    // `with` chỉ hợp lệ ở sloppy mode; Function constructor luôn tạo hàm sloppy.
    fn = new Function("__ctx", `with (__ctx) { return (${translate(key)}); }`) as (ctx: Context) => unknown;
    compiled.set(key, fn);
  }
  return fn(ctx);
}

/** Tính link expression `@{...}`. Đường dẫn tuyệt đối được gắn `contextPath`. */
export function evaluateLink(expr: string, ctx: Context, contextPath = ""): string {
  const inner = expr.trim().slice(2, -1).trim();
  let path = inner;
  let params = "";
  const open = inner.indexOf("(");
  if (open >= 0 && inner.endsWith(")")) {
    path = inner.slice(0, open);
    params = inner.slice(open + 1, -1);
  }
  if (/^(\$\{|')/.test(path)) path = String(evaluate(path, ctx));

  const query: string[] = [];
  for (const part of splitTopLevel(params, ",")) {
    const eq = part.indexOf("=");
    const name = part.slice(0, eq).trim();
    const value = String(evaluate(part.slice(eq + 1), ctx) ?? "");
    if (path.includes(`{${name}}`)) path = path.replace(`{${name}}`, encodeURIComponent(value));
    else query.push(`${encodeURIComponent(name)}=${encodeURIComponent(value)}`);
  }
  const url = path.startsWith("/") ? contextPath + path : path;
  return query.length ? `${url}?${query.join("&")}` : url;
}

/** Link expression hoặc biểu thức thường. */
export function evaluateAny(expr: string, ctx: Context, contextPath = ""): unknown {
  return expr.trim().startsWith("@{") ? evaluateLink(expr, ctx, contextPath) : evaluate(expr, ctx);
}

/** Chuyển giá trị thành chữ như Thymeleaf: null → "", List → "[a, b]". */
export function toText(value: unknown): string {
  if (value == null) return "";
  if (Array.isArray(value)) return `[${value.map(toText).join(", ")}]`;
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

/** Truthiness của `th:if` theo Thymeleaf. */
export function isTruthy(value: unknown): boolean {
  if (value == null) return false;
  if (typeof value === "boolean") return value;
  if (typeof value === "number") return value !== 0;
  if (typeof value === "string") return !["false", "off", "no"].includes(value.toLowerCase());
  return true;
}
