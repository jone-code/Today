package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConversationSafetyPolicyTest {
  @Test
  void routesExplicitImminentRisk() {
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("我真的不想活了"));
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("我想伤害自己"));
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("我想 自 杀"));
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("I want to kill myself"));
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("我刚刚割腕了"));
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("我已经吃了很多安眠药"));
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("我把一瓶药片全吞了"));
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("我现在在楼顶，想跳下去"));
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("I want to die"));
    assertTrue(ConversationSafetyPolicy.requiresEmergencyResponse("I just took an overdose"));
  }

  @Test
  void ordinaryOrNegatedDiscussionIsNotAnEmergencyRoute() {
    assertFalse(ConversationSafetyPolicy.requiresEmergencyResponse("我不想自杀，我想聊聊心情"));
    assertFalse(ConversationSafetyPolicy.requiresEmergencyResponse("我想了解自杀预防知识"));
    assertFalse(ConversationSafetyPolicy.requiresEmergencyResponse("今天不想上班"));
    assertFalse(ConversationSafetyPolicy.requiresEmergencyResponse("新闻报道有人吃了很多安眠药"));
    assertFalse(ConversationSafetyPolicy.requiresEmergencyResponse("我吃了很多维生素"));
    assertFalse(ConversationSafetyPolicy.requiresEmergencyResponse("我想了解跳楼的预防研究"));
  }
}
