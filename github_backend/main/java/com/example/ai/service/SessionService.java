package com.example.ai.service;

import com.example.ai.common.PageResult;
import com.example.ai.dto.LatestChainResponse;
import com.example.ai.entity.Session;
import com.example.ai.entity.Turn;
import com.example.ai.mapper.SessionMapper;
import com.example.ai.mapper.TurnMapper;
import com.example.ai.util.UuidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SessionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private TurnMapper turnMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TurnService turnService;

    private String kMeta(long uid, String sid) {
        return "user:" + uid + ":session:" + sid + ":meta";
    }

    private String kSessionsUpdated(long uid) {
        return "user:" + uid + ":sessions:updated";
    }

    /**
     * 创建会话
     * @param uid 用户ID
     * @param title 会话标题
     * @return 创建的会话
     */
    @Transactional
    public Session createSession(long uid, String title) {
        logger.info("创建会话: uid={}, title={}", uid, title);
        
        long now = Instant.now().getEpochSecond();
        UUID sid = UUID.randomUUID();
        Session session = new Session();
        session.setId(UuidUtils.uuidToBytes(sid));
        session.setUid(uid);
        session.setTitle(title == null ? "" : title);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setLastActiveTid(0);
        session.setTurnSeq(0);
        sessionMapper.insert(session);
        
        // 更新Redis缓存
        try {
            String sidStr = sid.toString();
            String metaKey = kMeta(uid, sidStr);
            stringRedisTemplate.opsForHash().put(metaKey, "title", session.getTitle());
            stringRedisTemplate.opsForHash().put(metaKey, "created_at", String.valueOf(session.getCreatedAt()));
            stringRedisTemplate.opsForHash().put(metaKey, "updated_at", String.valueOf(session.getUpdatedAt()));
            stringRedisTemplate.opsForHash().put(metaKey, "last_active_tid", String.valueOf(session.getLastActiveTid()));
            stringRedisTemplate.opsForZSet().add(kSessionsUpdated(uid), sidStr, session.getUpdatedAt());
        } catch (Exception e) {
            logger.warn("更新Redis缓存失败: {}", e.getMessage());
        }
        
        logger.info("会话创建成功: sid={}, title={}", sid, session.getTitle());
        return session;
    }

    /**
     * 分页查询会话
     * @param uid 用户ID
     * @param title 标题关键字
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<Session> pageSessions(long uid, String title, int page, int pageSize) {
        int actualPage = Math.max(page - 1, 0);
        int actualSize = Math.min(pageSize, 100);
        int offset = actualPage * actualSize;
        
        String searchTitle = (title == null || title.isEmpty()) ? null : title;
        List<Session> content = sessionMapper.selectByUserIdWithPagination(uid, searchTitle, offset, actualSize);
        long totalElements = sessionMapper.countByUserIdAndTitle(uid, searchTitle);
        
        return new PageResult<>(content, actualPage, actualSize, totalElements);
    }

    /**
     * 根据会话ID获取会话
     * @param sid 会话ID
     * @return 会话，如果不存在则返回空
     */
    public Optional<Session> getBySid(UUID sid) {
        Session session = sessionMapper.selectById(UuidUtils.uuidToBytes(sid));
        return Optional.ofNullable(session);
    }

    /**
     * 更新会话信息
     * @param sid 会话ID
     * @param newTitle 新标题
     * @param lastActiveTid 最后活跃的对话轮次ID
     * @param lastActiveTuid 最后活跃的对话轮次唯一标识
     * @return 更新后的会话，如果不存在则返回空
     */
    @Transactional
    public Optional<Session> updateSession(UUID sid, String newTitle, Integer lastActiveTid, UUID lastActiveTuid) {
        logger.info("更新会话: sid={}, newTitle={}, lastActiveTid={}", sid, newTitle, lastActiveTid);
        
        Session session = sessionMapper.selectById(UuidUtils.uuidToBytes(sid));
        if (session == null) {
            logger.warn("更新会话失败: 会话不存在 - sid={}", sid);
            return Optional.empty();
        }
        
        boolean changed = false;
        if (newTitle != null) { 
            session.setTitle(newTitle); 
            changed = true; 
        }
        if (lastActiveTid != null) { 
            session.setLastActiveTid(lastActiveTid); 
            changed = true; 
        }
        if (lastActiveTuid != null) { 
            session.setLastActiveTuid(UuidUtils.uuidToBytes(lastActiveTuid)); 
            changed = true; 
        }
        
        if (changed) {
            session.setUpdatedAt(Instant.now().getEpochSecond());
            sessionMapper.update(session);
            
            // 更新Redis缓存
            try {
                String metaKey = kMeta(session.getUid(), sid.toString());
                if (newTitle != null) stringRedisTemplate.opsForHash().put(metaKey, "title", session.getTitle());
                stringRedisTemplate.opsForHash().put(metaKey, "updated_at", String.valueOf(session.getUpdatedAt()));
                if (lastActiveTid != null) stringRedisTemplate.opsForHash().put(metaKey, "last_active_tid", String.valueOf(session.getLastActiveTid()));
                stringRedisTemplate.opsForZSet().add(kSessionsUpdated(session.getUid()), sid.toString(), session.getUpdatedAt());
            } catch (Exception e) {
                logger.warn("更新Redis缓存失败: {}", e.getMessage());
            }
        }
        
        logger.info("会话更新成功: sid={}", sid);
        return Optional.of(session);
    }

    /**
     * 删除会话
     * @param sid 会话ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteSession(UUID sid) {
        logger.info("删除会话: sid={}", sid);
        
        Session session = sessionMapper.selectById(UuidUtils.uuidToBytes(sid));
        if (session == null) {
            logger.warn("删除会话失败: 会话不存在 - sid={}", sid);
            return false;
        }
        
        // 先删除该会话下的所有对话轮次记录
        byte[] sidBytes = UuidUtils.uuidToBytes(sid);
        int deletedTurnsCount = turnMapper.deleteBySessionId(sidBytes);
        logger.info("删除会话关联的对话轮次: sid={}, 删除数量={}", sid, deletedTurnsCount);
        
        // 再删除会话记录
        sessionMapper.deleteById(sidBytes);
        
        // 更新Redis缓存
        try {
            String metaKey = kMeta(session.getUid(), sid.toString());
            stringRedisTemplate.delete(metaKey);
            stringRedisTemplate.opsForZSet().remove(kSessionsUpdated(session.getUid()), sid.toString());
            
            // 清理该会话相关的Redis缓存
            String childrenPattern = "user:" + session.getUid() + ":session:" + sid + ":children:*";
            stringRedisTemplate.delete(stringRedisTemplate.keys(childrenPattern));
            stringRedisTemplate.delete("user:" + session.getUid() + ":session:" + sid + ":turns:accessed");
            stringRedisTemplate.delete("user:" + session.getUid() + ":session:" + sid + ":heights");
        } catch (Exception e) {
            logger.warn("更新Redis缓存失败: {}", e.getMessage());
        }
        
        logger.info("会话删除成功: sid={}, 删除的对话轮次数={}", sid, deletedTurnsCount);
        return true;
    }

    /**
     * 获取最近活跃节点的路径链
     * @param sid 会话ID
     * @param includePayload 是否包含payload
     * @return 路径链响应
     */
    public Optional<LatestChainResponse> getLatestChain(UUID sid, boolean includePayload) {
        return getLatestChain(0L, sid, includePayload);
    }

    /**
     * 获取最近活跃节点的路径链（带高度信息）
     * @param uid 用户ID
     * @param sid 会话ID
     * @param includePayload 是否包含payload
     * @return 路径链响应
     */
    public Optional<LatestChainResponse> getLatestChain(long uid, UUID sid, boolean includePayload) {
        logger.info("获取最近活跃节点路径链: sid={}, includePayload={}", sid, includePayload);
        
        // 获取会话信息
        Session session = sessionMapper.selectById(UuidUtils.uuidToBytes(sid));
        if (session == null) {
            logger.warn("获取路径链失败: 会话不存在 - sid={}", sid);
            return Optional.empty();
        }
        
        // 获取最近活跃的对话轮次ID
        Integer lastActiveTid = session.getLastActiveTid();
        if (lastActiveTid == null || lastActiveTid <= 0) {
            logger.warn("获取路径链失败: 没有活跃的对话轮次 - sid={}", sid);
            return Optional.empty();
        }
        
        // 获取从根节点到最近活跃节点的完整路径链
        List<Turn> pathChain = getPathChainToRoot(UuidUtils.uuidToBytes(sid), lastActiveTid);
        if (pathChain.isEmpty()) {
            logger.warn("获取路径链失败: 路径链为空 - sid={}, lastActiveTid={}", sid, lastActiveTid);
            return Optional.empty();
        }
        
        // 为路径链设置高度信息
        if (uid > 0) {
            turnService.setHeightForTurns(pathChain, uid, sid);
        }
        
        // 构建路径TID列表
        List<Long> pathTidList = new ArrayList<>();
        List<LatestChainResponse.ChainNode> nodes = new ArrayList<>();
        
        for (Turn turn : pathChain) {
            pathTidList.add(turn.getTid().longValue());
            
            // 构建节点信息
            LatestChainResponse.ChainNode node = new LatestChainResponse.ChainNode();
            node.setTid(turn.getTid().longValue());
            node.setTuid(UuidUtils.bytesToUuid(turn.getTuid()).toString());
            node.setParentTid(turn.getParentTid() != null ? turn.getParentTid().longValue() : null);
            
            // 设置高度信息
            node.setHeight(turn.getHeight() != null ? turn.getHeight() : 0);
            
            // 根据参数决定是否包含payload
            if (includePayload) {
                node.setUserJson(parseJson(turn.getUserJson()));
                node.setAiJson(parseJson(turn.getAiJson()));
            } else {
                node.setUserJson(null);
                node.setAiJson(null);
            }
            
            nodes.add(node);
        }
        
        LatestChainResponse response = new LatestChainResponse(sid.toString(), pathTidList, nodes);
        logger.info("获取最近活跃节点路径链成功: sid={}, 路径长度={}", sid, pathChain.size());
        return Optional.of(response);
    }
    
    /**
     * 获取从指定节点到根节点的完整路径链
     * @param sidBytes 会话ID字节数组
     * @param currentTid 当前对话轮次ID
     * @return 对话轮次列表（从根节点到当前节点，按时间顺序）
     */
    private List<Turn> getPathChainToRoot(byte[] sidBytes, Integer currentTid) {
        List<Turn> pathChain = new ArrayList<>();
        collectPathChainToRootRecursively(sidBytes, currentTid, pathChain);
        
        // 反转列表，使其从根节点到当前节点按时间顺序排列
        Collections.reverse(pathChain);
        return pathChain;
    }
    
    /**
     * 递归向上追溯路径链到根节点
     * @param sidBytes 会话ID字节数组
     * @param tid 当前对话轮次ID
     * @param result 结果列表
     */
    private void collectPathChainToRootRecursively(byte[] sidBytes, Integer tid, List<Turn> result) {
        if (tid == null || tid <= 0) {
            return; // 到达根节点
        }
        
        // 查询当前节点
        Turn currentTurn = turnMapper.selectBySessionIdAndTid(sidBytes, tid);
        if (currentTurn != null) {
            result.add(currentTurn);
            // 递归查询父节点
            collectPathChainToRootRecursively(sidBytes, currentTurn.getParentTid(), result);
        }
    }
    
    /**
     * 解析JSON字符串为对象
     * @param jsonStr JSON字符串
     * @return 解析后的对象，如果解析失败则返回null
     */
    private Object parseJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(jsonStr, Object.class);
        } catch (JsonProcessingException e) {
            logger.warn("JSON解析失败: {}", e.getMessage());
            return null;
        }
    }
}
