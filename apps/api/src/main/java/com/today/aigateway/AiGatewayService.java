package com.today.aigateway;

import com.today.common.AiProvider;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** 唯一允许接触模型 SDK 的模块；无 key 时降级 Heuristic */
@Service
public class AiGatewayService {

  public AiProvider getActiveProvider() {
    String key = System.getenv("OPENAI_API_KEY");
    return key != null && !key.isBlank() ? AiProvider.llm : AiProvider.heuristic;
  }

  public <T> AiResult<T> complete(AiTask task, Object input, Supplier<T> heuristic) {
    // TODO: task 对应 prompt + LLM；失败或无 key 时走 heuristic
    if (getActiveProvider() == AiProvider.heuristic) {
      return new AiResult<>(heuristic.get(), AiProvider.heuristic);
    }
    return new AiResult<>(heuristic.get(), AiProvider.heuristic);
  }

  public enum AiTask {
    summary,
    memory_extract,
    proactive
  }

  public record AiResult<T>(T data, AiProvider provider) {}
}
