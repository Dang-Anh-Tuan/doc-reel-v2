import { describe, expect, it } from "vitest";
import { evaluate, evaluateLink, isTruthy, splitTopLevel, toText, translate } from "./expression";

const ctx = {
  scene: { accent: "Rules", noteKind: null as string | null, answer: 1, items: ["a", "b"] },
  video: { theme: null as string | null },
  st: { index: 1, count: 2 },
};

describe("translate", () => {
  it("dịch ${...}, chuỗi và toán tử chữ", () => {
    expect(translate("${a} and not ${b}").replace(/\s+/g, " ")).toBe("(a) && ! (b)");
    expect(translate("'x' + ${n}")).toBe('"x" + (n)');
  });

  it("không đụng vào identifier chứa chữ giống toán tử", () => {
    expect(translate("${scene.noteKind}")).toBe("(scene.noteKind)");
    expect(translate("${order}")).toBe("(order)");
  });

  it("dịch Elvis ?: thành ??", () => {
    expect(translate("${a ?: 'b'}")).toBe('(a ?? "b")');
  });

  it("từ chối utility object", () => {
    expect(() => translate("${#lists.size(x)}")).toThrow(/utility object/);
  });
});

describe("evaluate", () => {
  it("truy cập thuộc tính, so sánh, ternary", () => {
    expect(evaluate("${scene.accent}", ctx)).toBe("Rules");
    expect(evaluate("${st.index == scene.answer} ? 'is-answer' : ''", ctx)).toBe("is-answer");
    expect(evaluate("${st.count < 10 ? '0' + st.count : st.count}", ctx)).toBe("02");
  });

  it("gọi method của chuỗi như SpEL", () => {
    expect(evaluate("${'ABCD'.charAt(st.index)}", ctx)).toBe("B");
  });

  it("Elvis lấy giá trị mặc định khi null", () => {
    expect(evaluate("${'note--' + (scene.noteKind ?: 'info')}", ctx)).toBe("note--info");
  });
});

describe("evaluateLink", () => {
  it("thay path variable và gắn context path", () => {
    expect(evaluateLink("@{/css/themes/{t}.css(t=${video.theme ?: 'midnight-brass'})}", ctx)).toBe(
      "/css/themes/midnight-brass.css",
    );
    expect(evaluateLink("@{/js/slide.js}", ctx, "/app")).toBe("/app/js/slide.js");
  });

  it("tham số thừa thành query string", () => {
    expect(evaluateLink("@{/preview(type=${scene.accent},frame=2)}", ctx)).toBe("/preview?type=Rules&frame=2");
  });
});

describe("helpers", () => {
  it("splitTopLevel bỏ qua dấu phẩy trong ngoặc và chuỗi", () => {
    expect(splitTopLevel("a=${f(x, y)},b='1,2',c=3", ",")).toEqual(["a=${f(x, y)}", "b='1,2'", "c=3"]);
  });

  it("toText giống Thymeleaf", () => {
    expect(toText(null)).toBe("");
    expect(toText([2, 3])).toBe("[2, 3]");
  });

  it("isTruthy giống th:if", () => {
    expect(isTruthy("false")).toBe(false);
    expect(isTruthy("off")).toBe(false);
    expect(isTruthy("")).toBe(true);
    expect(isTruthy(0)).toBe(false);
    expect(isTruthy([])).toBe(true);
  });
});
