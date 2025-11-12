package com.example.ai.service;

import com.example.ai.common.PageResult;
import com.example.ai.entity.Session;
import com.example.ai.entity.Turn;
import com.example.ai.exception.BusinessException;
import com.example.ai.mapper.SessionMapper;
import com.example.ai.mapper.TurnMapper;
import com.example.ai.util.UuidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TurnService {
    
    private static final Logger logger = LoggerFactory.getLogger(TurnService.class);

    @Autowired
    private TurnMapper turnMapper;

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private HashOperations<String, String, String> hashOps;

    private String kChildren(long uid, String sid, String parent) {
        return "user:" + uid + ":session:" + sid + ":children:" + parent;
    }

    private String kTurnsAccessed(long uid, String sid) {
        return "user:" + uid + ":session:" + sid + ":turns:accessed";
    }

    private String kHeights(long uid, String sid) {
        return "user:" + uid + ":session:" + sid + ":heights";
    }

    /**
     * 创建对话轮次
     * @param uid 用户ID
     * @param sid 会话ID
     * @param parentTid 父对话轮次ID
     * @param userJson 用户输入JSON
     * @param aiJson AI响应JSON
     * @return 创建的对话轮次
     */
    @Transactional
    public Turn createTurn(long uid, UUID sid, Integer parentTid, String userJson, String aiJson) {
        logger.info("创建对话轮次: uid={}, sid={}, parentTid={}", uid, sid, parentTid);
        
        byte[] sidBytes = UuidUtils.uuidToBytes(sid);
        Session session = sessionMapper.selectById(sidBytes);
        if (session == null) {
            logger.warn("创建对话轮次失败: 会话不存在 - sid={}", sid);
            throw new BusinessException(1005, "会话不存在");
        }
        
        int nextTid = session.getTurnSeq() + 1;
        session.setTurnSeq(nextTid);
        session.setLastActiveTid(nextTid);
        
        long now = Instant.now().getEpochSecond();
        

        UUID tuid = UUID.randomUUID();
        session.setLastActiveTuid(UuidUtils.uuidToBytes(tuid));
        session.setUpdatedAt(now);
        sessionMapper.update(session);
        Turn turn = new Turn();
        turn.setTuid(UuidUtils.uuidToBytes(tuid));
        turn.setUid(uid);
        turn.setSessionId(sidBytes);
        turn.setTid(nextTid);
        turn.setParentTid(parentTid);
        turn.setUserJson(userJson);
        turn.setAiJson(aiJson);
        turn.setCreatedAt(now);
        turn.setLastAccessedAt(now);
        turnMapper.insert(turn);

        // 更新Redis缓存
        try {
            String sidStr = sid.toString();
            String parentKey = kChildren(uid, sidStr, parentTid == null ? "root" : String.valueOf(parentTid));
            stringRedisTemplate.opsForSet().add(parentKey, String.valueOf(nextTid));
            stringRedisTemplate.opsForZSet().add(kTurnsAccessed(uid, sidStr), String.valueOf(nextTid), now);
        } catch (Exception e) {
            logger.warn("更新Redis缓存失败: {}", e.getMessage());
        }

        logger.info("对话轮次创建成功: tid={}, tuid={}", nextTid, tuid);
        return turn;
    }

    /**
     * 根据会话ID和对话轮次ID获取对话轮次
     * @param sid 会话ID
     * @param tid 对话轮次ID
     * @return 对话轮次，如果不存在则返回空
     */
    public Optional<Turn> getTurn(UUID sid, int tid) {
        Turn turn = turnMapper.selectBySessionIdAndTid(UuidUtils.uuidToBytes(sid), tid);
        return Optional.ofNullable(turn);
    }

    /**
     * 根据TUID获取对话轮次
     * @param tuid 对话轮次唯一标识
     * @return 对话轮次，如果不存在则返回空
     */
    public Optional<Turn> getByTuid(UUID tuid) {
        Turn turn = turnMapper.selectByTuid(UuidUtils.uuidToBytes(tuid));
        return Optional.ofNullable(turn);
    }

    /**
     * 分页查询对话轮次
     * @param sid 会话ID
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<Turn> pageTurns(UUID sid, int page, int pageSize) {
        int actualPage = Math.max(page - 1, 0);
        int actualSize = Math.min(pageSize, 100);
        int offset = actualPage * actualSize;
        
        List<Turn> content = turnMapper.selectBySessionIdWithPagination(UuidUtils.uuidToBytes(sid), offset, actualSize);
        long totalElements = turnMapper.countBySessionId(UuidUtils.uuidToBytes(sid));
        
        return new PageResult<>(content, actualPage, actualSize, totalElements);
    }

    /**
     * 删除对话轮次
     * @param uid 用户ID
     * @param sid 会话ID
     * @param tid 对话轮次ID
     * @param subtree 是否删除子树
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteTurn(long uid, UUID sid, int tid, boolean subtree) {
        logger.info("删除对话轮次: uid={}, sid={}, tid={}, subtree={}", uid, sid, tid, subtree);
        
        Turn turn = turnMapper.selectBySessionIdAndTid(UuidUtils.uuidToBytes(sid), tid);
        if (turn == null) {
            logger.warn("删除对话轮次失败: 对话轮次不存在 - tid={}", tid);
            return false;
        }
        
        // 获取当前会话信息
        byte[] sidBytes = UuidUtils.uuidToBytes(sid);
        Session session = sessionMapper.selectById(sidBytes);
        if (session == null) {
            logger.warn("删除对话轮次失败: 会话不存在 - sid={}", sid);
            return false;
        }
        
        List<Turn> toDelete = new ArrayList<>();
        if (subtree) {
            collectSubtree(sidBytes, tid, toDelete);
        } else {
            toDelete.add(turn);
        }
        
        // 检查要删除的节点中是否包含当前会话的last_active_tid
        Integer currentLastActiveTid = session.getLastActiveTid();
        boolean needUpdateLastActiveTid = false;
        Integer newLastActiveTid = null;
        
        if (currentLastActiveTid != null && currentLastActiveTid > 0) {
            for (Turn t : toDelete) {
                if (t.getTid().equals(currentLastActiveTid)) {
                    needUpdateLastActiveTid = true;
                    newLastActiveTid = turn.getParentTid();
                    logger.info("检测到删除的节点包含last_active_tid: tid={}, 将更新为父节点: parentTid={}", 
                              currentLastActiveTid, newLastActiveTid);
                    break;
                }
            }
        }
        
        // 执行删除操作
        for (Turn t : toDelete) {
            turnMapper.deleteById(t.getId());
        }
        
        // 如果需要更新last_active_tid，则更新会话信息
        if (needUpdateLastActiveTid) {
            session.setLastActiveTid(newLastActiveTid);
            session.setUpdatedAt(Instant.now().getEpochSecond());
            sessionMapper.update(session);
            logger.info("已更新会话last_active_tid: sid={}, 新值={}", sid, newLastActiveTid);
        }
        
        // 更新Redis缓存
        try {
            String sidStr = sid.toString();
            stringRedisTemplate.opsForZSet().remove(kTurnsAccessed(uid, sidStr), String.valueOf(tid));
            stringRedisTemplate.delete(kChildren(uid, sidStr, String.valueOf(tid)));
            
            // 如果更新了last_active_tid，同时更新Redis缓存
            if (needUpdateLastActiveTid) {
                String metaKey = "user:" + uid + ":session:" + sidStr + ":meta";
                stringRedisTemplate.opsForHash().put(metaKey, "last_active_tid", 
                    String.valueOf(newLastActiveTid != null ? newLastActiveTid : 0));
                stringRedisTemplate.opsForHash().put(metaKey, "updated_at", 
                    String.valueOf(session.getUpdatedAt()));
            }
        } catch (Exception e) {
            logger.warn("更新Redis缓存失败: {}", e.getMessage());
        }
        
        logger.info("对话轮次删除成功: tid={}, 删除数量={}, 更新last_active_tid={}", 
                  tid, toDelete.size(), needUpdateLastActiveTid);
        return true;
    }

    /**
     * 递归收集子树中的所有对话轮次
     * @param sidBytes 会话ID字节数组
     * @param parentTid 父对话轮次ID
     * @param out 输出列表
     */
    private void collectSubtree(byte[] sidBytes, Integer parentTid, List<Turn> out) {
        Turn parent = turnMapper.selectBySessionIdAndTid(sidBytes, parentTid);
        if (parent != null) {
            out.add(parent);
        }
        List<Turn> children = turnMapper.selectBySessionIdAndParentTid(sidBytes, parentTid);
        for (Turn child : children) {
            collectSubtree(sidBytes, child.getTid(), out);
        }
    }
    
    /**
     * 获取对话链（从指定父节点开始的所有子对话）
     * @param sid 会话ID
     * @param parentTid 父对话轮次ID
     * @return 对话轮次列表（按时间顺序）
     */
    public List<Turn> getConversationChain(UUID sid, Integer parentTid) {
        logger.info("获取对话链: sid={}, parentTid={}", sid, parentTid);
        
        List<Turn> conversationChain = new ArrayList<>();
        collectConversationChainRecursively(UuidUtils.uuidToBytes(sid), parentTid, conversationChain);
        
        logger.info("获取对话链成功: sid={}, parentTid={}, 对话数量={}", sid, parentTid, conversationChain.size());
        return conversationChain;
    }

    /**
     * 获取对话链（从指定父节点开始的所有子对话）- 带高度信息
     * @param uid 用户ID
     * @param sid 会话ID
     * @param parentTid 父对话轮次ID
     * @return 对话轮次列表（按时间顺序，包含高度信息）
     */
    public List<Turn> getConversationChainWithHeight(long uid, UUID sid, Integer parentTid) {
        logger.info("获取对话链（带高度）: uid={}, sid={}, parentTid={}", uid, sid, parentTid);
        
        List<Turn> conversationChain = new ArrayList<>();
        collectConversationChainRecursively(UuidUtils.uuidToBytes(sid), parentTid, conversationChain);
        
        // 为对话链设置高度信息
        setHeightForTurns(conversationChain, uid, sid);
        
        logger.info("获取对话链成功（带高度）: sid={}, parentTid={}, 对话数量={}", sid, parentTid, conversationChain.size());
        return conversationChain;
    }
    
    /**
     * 递归收集对话链
     * @param sidBytes 会话ID字节数组
     * @param parentTid 父对话轮次ID
     * @param result 结果列表
     */
    private void collectConversationChainRecursively(byte[] sidBytes, Integer parentTid, List<Turn> result) {
        // 查询直接子节点
        List<Turn> directChildren = turnMapper.selectConversationChain(sidBytes, parentTid);
        
        for (Turn child : directChildren) {
            result.add(child);
            // 递归查询子节点的子节点
            collectConversationChainRecursively(sidBytes, child.getTid(), result);
        }
    }
    
    /**
     * 获取从指定节点到根节点的完整对话链（向上追溯）
     * @param sid 会话ID
     * @param currentTid 当前对话轮次ID
     * @return 对话轮次列表（从根节点到当前节点，按时间顺序）
     */
    public List<Turn> getConversationChainToRoot(UUID sid, Integer currentTid) {
        logger.info("获取到根节点的对话链: sid={}, currentTid={}", sid, currentTid);
        
        List<Turn> conversationChain = new ArrayList<>();
        collectConversationChainToRootRecursively(UuidUtils.uuidToBytes(sid), currentTid, conversationChain);
        
        // 反转列表，使其从根节点到当前节点按时间顺序排列
        Collections.reverse(conversationChain);
        
        logger.info("获取到根节点的对话链成功: sid={}, currentTid={}, 对话数量={}", sid, currentTid, conversationChain.size());
        return conversationChain;
    }

    /**
     * 获取从指定节点到根节点的完整对话链（向上追溯）- 带高度信息
     * @param uid 用户ID
     * @param sid 会话ID
     * @param currentTid 当前对话轮次ID
     * @return 对话轮次列表（从根节点到当前节点，按时间顺序，包含高度信息）
     */
    public List<Turn> getConversationChainToRootWithHeight(long uid, UUID sid, Integer currentTid) {
        logger.info("获取到根节点的对话链（带高度）: uid={}, sid={}, currentTid={}", uid, sid, currentTid);
        
        List<Turn> conversationChain = new ArrayList<>();
        collectConversationChainToRootRecursively(UuidUtils.uuidToBytes(sid), currentTid, conversationChain);
        
        // 反转列表，使其从根节点到当前节点按时间顺序排列
        Collections.reverse(conversationChain);
        
        // 为对话链设置高度信息
        setHeightForTurns(conversationChain, uid, sid);
        
        logger.info("获取到根节点的对话链成功（带高度）: sid={}, currentTid={}, 对话数量={}", sid, currentTid, conversationChain.size());
        return conversationChain;
    }
    
    /**
     * 递归向上追溯对话链到根节点
     * @param sidBytes 会话ID字节数组
     * @param tid 当前对话轮次ID
     * @param result 结果列表
     */
    private void collectConversationChainToRootRecursively(byte[] sidBytes, Integer tid, List<Turn> result) {
        if (tid == null || tid <= 0) {
            return; // 到达根节点
        }
        
        // 查询当前节点
        Turn currentTurn = turnMapper.selectBySessionIdAndTid(sidBytes, tid);
        if (currentTurn != null) {
            result.add(currentTurn);
            // 递归查询父节点
            collectConversationChainToRootRecursively(sidBytes, currentTurn.getParentTid(), result);
        }
    }

    /**
     * 加载会话内所有节点的高度信息到Redis
     * @param uid 用户ID
     * @param sid 会话ID
     */
    public void loadHeightsToRedis(long uid, UUID sid) {
        logger.info("开始加载节点高度信息到Redis: uid={}, sid={}", uid, sid);
        
        byte[] sidBytes = UuidUtils.uuidToBytes(sid);
        String sidStr = sid.toString();
        
        // 获取会话内所有节点
        List<Turn> allTurns = turnMapper.selectBySessionId(sidBytes);
        if (allTurns.isEmpty()) {
            logger.info("会话内没有节点，跳过高度计算: sid={}", sid);
            return;
        }
        
        // 构建节点映射
        Map<Integer, Turn> turnMap = new HashMap<>();
        for (Turn turn : allTurns) {
            turnMap.put(turn.getTid(), turn);
        }
        
        // 计算每个节点的高度
        Map<Integer, Integer> heights = calculateHeights(turnMap);
        
        // 存储到Redis
        try {
            String heightsKey = kHeights(uid, sidStr);
            hashOps = stringRedisTemplate.opsForHash();
            
            // 清除旧的高度数据
            stringRedisTemplate.delete(heightsKey);
            
            // 存储新的高度数据
            for (Map.Entry<Integer, Integer> entry : heights.entrySet()) {
                hashOps.put(heightsKey, String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            
            logger.info("节点高度信息加载完成: sid={}, 节点数量={}", sid, heights.size());
        } catch (Exception e) {
            logger.error("存储高度信息到Redis失败: {}", e.getMessage(), e);
            throw new BusinessException(5000, "存储高度信息失败");
        }
    }

    /**
     * 计算节点高度
     * @param turnMap 节点映射
     * @return 节点高度映射
     */
    private Map<Integer, Integer> calculateHeights(Map<Integer, Turn> turnMap) {
        Map<Integer, Integer> heights = new ConcurrentHashMap<>();
        
        // 为每个节点计算高度
        for (Turn turn : turnMap.values()) {
            int height = calculateNodeHeight(turn.getTid(), turnMap, heights);
            heights.put(turn.getTid(), height);
        }
        
        return heights;
    }

    /**
     * 递归计算单个节点的高度
     * 高度定义：从该节点到其所有子孙节点的最长路径的边数
     * @param tid 节点ID
     * @param turnMap 节点映射
     * @param heights 已计算的高度缓存
     * @return 节点高度
     */
    private int calculateNodeHeight(Integer tid, Map<Integer, Turn> turnMap, Map<Integer, Integer> heights) {
        // 如果已经计算过，直接返回
        if (heights.containsKey(tid)) {
            return heights.get(tid);
        }
        
        Turn turn = turnMap.get(tid);
        if (turn == null) {
            return 0;
        }
        
        // 找到所有子节点
        List<Integer> children = new ArrayList<>();
        for (Turn child : turnMap.values()) {
            if (child.getParentTid() != null && child.getParentTid().equals(tid)) {
                children.add(child.getTid());
            }
        }
        
        // 如果没有子节点，高度为0（叶子节点）
        if (children.isEmpty()) {
            heights.put(tid, 0);
            return 0;
        }
        
        // 计算所有子节点的最大高度
        int maxChildHeight = 0;
        for (Integer childTid : children) {
            int childHeight = calculateNodeHeight(childTid, turnMap, heights);
            maxChildHeight = Math.max(maxChildHeight, childHeight);
        }
        
        // 当前节点的高度 = 最大子节点高度 + 1
        int currentHeight = maxChildHeight + 1;
        heights.put(tid, currentHeight);
        
        return currentHeight;
    }

    /**
     * 获取指定节点及其父链的高度信息
     * @param uid 用户ID
     * @param sid 会话ID
     * @param tid 节点ID
     * @return 父链及当前节点的高度信息列表
     */
    public List<Map<String, Object>> getNodeHeights(long uid, UUID sid, Integer tid) {
        logger.info("获取节点高度信息: uid={}, sid={}, tid={}", uid, sid, tid);
        
        try {
            // 获取指定节点到根节点的路径（带高度信息）
            List<Turn> chainToRoot = getConversationChainToRootWithHeight(uid, sid, tid);
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (Turn turn : chainToRoot) {
                Map<String, Object> nodeHeight = new HashMap<>();
                nodeHeight.put("tid", turn.getTid());
                nodeHeight.put("height", turn.getHeight() != null ? turn.getHeight() : 0);
                result.add(nodeHeight);
            }
            
            logger.info("获取节点高度信息成功: sid={}, tid={}, 节点数量={}", sid, tid, result.size());
            return result;
            
        } catch (Exception e) {
            logger.error("获取节点高度信息失败: {}", e.getMessage(), e);
            throw new BusinessException(5000, "获取节点高度信息失败");
        }
    }

    /**
     * 获取指定节点的所有子节点
     * @param uid 用户ID
     * @param sid 会话ID
     * @param tid 节点ID
     * @param includePayload 是否包含载荷数据
     * @return 子节点列表
     */
    public List<Turn> getChildren(long uid, UUID sid, Integer tid, boolean includePayload) {
        logger.info("获取子节点: uid={}, sid={}, tid={}, includePayload={}", uid, sid, tid, includePayload);
        
        byte[] sidBytes = UuidUtils.uuidToBytes(sid);
        
        try {
            // 获取直接子节点
            List<Turn> children = turnMapper.selectBySessionIdAndParentTid(sidBytes, tid);
            
            // 为子节点设置高度信息
            setHeightForTurns(children, uid, sid);
            
            logger.info("获取子节点成功: sid={}, tid={}, 子节点数量={}", sid, tid, children.size());
            return children;
            
        } catch (Exception e) {
            logger.error("获取子节点失败: {}", e.getMessage(), e);
            throw new BusinessException(5000, "获取子节点失败");
        }
    }

    /**
     * 构建子节点响应数据
     * @param sid 会话ID字符串
     * @param turn 对话轮次实体
     * @param includePayload 是否包含载荷数据
     * @return 响应数据
     */
    private Map<String, Object> buildChildResponse(String sid, Turn turn, boolean includePayload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tid", turn.getTid());
        data.put("tuid", UuidUtils.bytesToUuid(turn.getTuid()).toString());
        data.put("parent_tid", turn.getParentTid());
        
        if (includePayload) {
            data.put("user_json", parseJsonOrNull(turn.getUserJson()));
            data.put("ai_json", parseJsonOrNull(turn.getAiJson()));
        }
        
        data.put("created_at", Instant.ofEpochSecond(turn.getCreatedAt()).toString());
        data.put("last_accessed_at", Instant.ofEpochSecond(turn.getLastAccessedAt()).toString());
        
        return data;
    }

    /**
     * 为Turn实体设置高度信息
     * @param turn Turn实体
     * @param uid 用户ID
     * @param sid 会话ID
     */
    public void setHeightForTurn(Turn turn, long uid, UUID sid) {
        if (turn == null) return;
        
        try {
            String sidStr = sid.toString();
            String heightsKey = kHeights(uid, sidStr);
            hashOps = stringRedisTemplate.opsForHash();
            
            String heightStr = (String) hashOps.get(heightsKey, String.valueOf(turn.getTid()));
            int height = heightStr != null ? Integer.parseInt(heightStr) : 0;
            turn.setHeight(height);
        } catch (Exception e) {
            logger.warn("设置节点高度失败: tid={}, error={}", turn.getTid(), e.getMessage());
            turn.setHeight(0);
        }
    }

    /**
     * 为Turn列表设置高度信息
     * @param turns Turn列表
     * @param uid 用户ID
     * @param sid 会话ID
     */
    public void setHeightForTurns(List<Turn> turns, long uid, UUID sid) {
        if (turns == null || turns.isEmpty()) return;
        
        try {
            String sidStr = sid.toString();
            String heightsKey = kHeights(uid, sidStr);
            hashOps = stringRedisTemplate.opsForHash();
            
            for (Turn turn : turns) {
                String heightStr = (String) hashOps.get(heightsKey, String.valueOf(turn.getTid()));
                int height = heightStr != null ? Integer.parseInt(heightStr) : 0;
                turn.setHeight(height);
            }
        } catch (Exception e) {
            logger.warn("设置节点高度失败: error={}", e.getMessage());
            for (Turn turn : turns) {
                turn.setHeight(0);
            }
        }
    }

    /**
     * 解析JSON字符串为Map，如果解析失败返回null
     * @param jsonString JSON字符串
     * @return Map对象或null
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonOrNull(String jsonString) {
        if (jsonString == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(jsonString, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
