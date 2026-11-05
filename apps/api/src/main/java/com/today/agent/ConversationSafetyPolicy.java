package com.today.agent;

import java.util.Locale;
import java.util.regex.Pattern;

/** A narrow deterministic emergency route, separate from the ordinary companion prompt. */
final class ConversationSafetyPolicy {
  private static final Pattern IMMEDIATE_RISK = Pattern.compile(
      "(?<![不没])(?:想|要|准备|打算|计划|可能)(?![^。]{0,8}(?:讨论|了解|预防|阻止|帮助|报道|新闻|研究)).{0,8}(?:自杀|轻生|结束.{0,3}生命|伤害自己|割腕|跳楼|自残)"
          + "|(?:已经|刚刚).{0,4}(?:割腕|吞药|自残)"
          + "|(?:不想活了|活不下去了|我想死|我想结束生命|不如死了)"
          + "|(?:i want to kill myself|i'm going to kill myself|i want to end my life|i am suicidal)"
  );

  private static final Pattern ACUTE_OVERDOSE_OR_JUMP = Pattern.compile(
      "我(?:已经|刚刚|刚)?(?:吃了|吞了|服了)(?:一整瓶|一瓶|大量|很多|过量)(?:的)?(?:安眠药|药片|药物)"
          + "|我(?:已经|刚刚|刚)?(?:把)?(?:一整瓶|一瓶|大量|很多)(?:安眠药|药片|药物)(?:都|全)?(?:吃了|吞了|服了)"
          + "|我(?:现在)?(?:站在|在)(?:楼顶|天台|桥边|桥上).{0,6}(?:要|想|准备)(?:跳下去|跳楼|跳桥)"
          + "|(?:i want to die|i (?:just )?took an overdose)"
  );

  static final String RESPONSE = "听起来你现在可能很难受。你的安全比继续聊天更重要。如果你可能马上伤害自己，"
      + "或已经采取行动，请立刻联系当地急救服务；在中国大陆可拨打 120 或 110。"
      + "请尽量去有人在的地方，联系一位你信任的人，让对方现在陪着你。我是 AI，无法提供紧急救援。你现在安全吗？";

  private ConversationSafetyPolicy() {}

  static boolean requiresEmergencyResponse(String text) {
    if (text == null) return false;
    String normalized = text.toLowerCase(Locale.ROOT);
    String compact = normalized.replaceAll("\\s+", "");
    return IMMEDIATE_RISK.matcher(compact).find() || IMMEDIATE_RISK.matcher(normalized).find()
        || ACUTE_OVERDOSE_OR_JUMP.matcher(compact).find()
        || ACUTE_OVERDOSE_OR_JUMP.matcher(normalized).find();
  }
}
