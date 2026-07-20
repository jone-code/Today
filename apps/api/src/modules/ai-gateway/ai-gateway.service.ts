import { Injectable } from "@nestjs/common";

export type AiTask = "summary" | "memory_extract" | "proactive";

export type AiCompleteResult<T> = {
  data: T;
  provider: "llm" | "heuristic";
};

/**
 * AI 网关占位：业务模块只依赖本服务，不直接引用模型 SDK。
 * 后续在此接入 LlmProvider，并在无 key 时降级 HeuristicProvider。
 */
@Injectable()
export class AiGatewayService {
  getActiveProvider(): "llm" | "heuristic" {
    return process.env.OPENAI_API_KEY ? "llm" : "heuristic";
  }

  async complete<T>(
    task: AiTask,
    _input: unknown,
    heuristic: () => T,
  ): Promise<AiCompleteResult<T>> {
    // TODO: task 对应 prompt + LLM；失败或无 key 时走 heuristic
    void task;
    if (this.getActiveProvider() === "heuristic") {
      return { data: heuristic(), provider: "heuristic" };
    }
    // LLM 路径尚未接通，先安全降级，保证模块可运行
    return { data: heuristic(), provider: "heuristic" };
  }
}
