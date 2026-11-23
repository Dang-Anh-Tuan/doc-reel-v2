/**
 * Kiểu dữ liệu kịch bản video — khớp SPEC mục 9.1 và các record Java (`script/Scene`).
 * Dùng chung cho trang preview template và trang quản trị job.
 */

export const THEMES = [
  "midnight-brass",
  "petrol-sage",
  "indigo-dust",
  "navy-terracotta",
  "slate-mustard",
  "deep-ocean-coral",
  "blueprint-cream",
  "storm-rose",
  "teal-navy-copper",
  "prussian-olive",
] as const;
export type ThemeName = (typeof THEMES)[number];
export const DEFAULT_THEME: ThemeName = "midnight-brass";

export type VideoType = "SLIDES" | "CHEATSHEET";
export type NoteKind = "info" | "warn" | "tip";
export type CompareKind = "neutral" | "bad" | "good";

/** Phần thông tin video mà template cần (model `video`). */
export interface VideoMeta {
  topicId: string;
  track: string;
  title: string;
  theme?: ThemeName;
}

export interface Origin {
  channel: "inbox" | "rest" | "telegram" | "zalo";
  chatId?: string;
  messageId?: string;
}

export interface VideoScript extends VideoMeta {
  version: number;
  videoType: VideoType;
  sources?: string[];
  scenes: Scene[];
  origin?: Origin;
}

interface SceneBase {
  id: string;
  narration: string;
  /** Thay `narration` khi đọc (phụ đề vẫn dùng narration). */
  spoken?: string;
}

/** Scene có tiêu đề chung: `accent` trong pill + `titleRest`. */
interface Titled {
  accent: string;
  titleRest?: string;
}

export interface TitleScene extends SceneBase, Titled {
  type: "title";
  chip?: string;
  hook: string;
}

export interface DefinitionScene extends SceneBase {
  type: "definition";
  term: string;
  expansion?: string;
  definition: string;
  example?: string;
}

export interface PointsScene extends SceneBase, Titled {
  type: "points";
  points: Array<{ heading: string; detail: string }>;
}

export interface StepsScene extends SceneBase, Titled {
  type: "steps";
  steps: string[];
}

export interface CodeScene extends SceneBase, Titled {
  type: "code";
  file?: string;
  lang: string;
  code: string;
  /** Số dòng cần highlight, đếm từ 1. */
  highlight?: number[];
  note?: string;
  noteKind?: NoteKind;
}

export interface CodeBlock {
  label: string;
  code: string;
}

export interface CodeCompareScene extends SceneBase, Titled {
  type: "codeCompare";
  lang: string;
  bad: CodeBlock;
  good: CodeBlock;
  note?: string;
}

export interface CompareColumn {
  heading: string;
  kind?: CompareKind;
  items: string[];
}

export interface CompareScene extends SceneBase, Titled {
  type: "compare";
  left: CompareColumn;
  right: CompareColumn;
  note?: string;
}

export interface TableScene extends SceneBase, Titled {
  type: "table";
  columns: string[];
  rows: string[][];
}

export interface QuizScene extends SceneBase {
  type: "quiz";
  question: string;
  options: string[];
  /** Index đáp án đúng, đếm từ 0. */
  answer: number;
  explain: string;
  revealNarration: string;
}

export interface SummaryScene extends SceneBase, Titled {
  type: "summary";
  bullets: string[];
  outro?: string;
}

export type Scene =
  | TitleScene
  | DefinitionScene
  | PointsScene
  | StepsScene
  | CodeScene
  | CodeCompareScene
  | CompareScene
  | TableScene
  | QuizScene
  | SummaryScene;

export type SceneType = Scene["type"];

/** Thứ tự hiển thị trong preview. */
export const SCENE_TYPES = [
  "title",
  "definition",
  "points",
  "steps",
  "code",
  "codeCompare",
  "compare",
  "table",
  "quiz",
  "summary",
] as const satisfies readonly SceneType[];

/** Số frame cần chụp cho mỗi scene (quiz: câu hỏi + đáp án). */
export function frameCount(type: SceneType): number {
  return type === "quiz" ? 2 : 1;
}

export function isSceneType(value: unknown): value is SceneType {
  return typeof value === "string" && (SCENE_TYPES as readonly string[]).includes(value);
}

export function isThemeName(value: unknown): value is ThemeName {
  return typeof value === "string" && (THEMES as readonly string[]).includes(value);
}
