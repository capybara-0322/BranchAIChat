package com.example.ai.mapper;

import com.example.ai.entity.Turn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话轮次数据访问层
 */
@Mapper
public interface TurnMapper {
    
    /**
     * 插入对话轮次
     * @param turn 对话轮次信息
     * @return 影响行数
     */
    int insert(Turn turn);
    
    /**
     * 根据ID查询对话轮次
     * @param id 对话轮次ID
     * @return 对话轮次信息
     */
    Turn selectById(@Param("id") Long id);
    
    /**
     * 根据TUID查询对话轮次
     * @param tuid 对话轮次唯一标识
     * @return 对话轮次信息
     */
    Turn selectByTuid(@Param("tuid") byte[] tuid);
    
    /**
     * 根据会话ID和TID查询对话轮次
     * @param sessionId 会话ID
     * @param tid 对话轮次序号
     * @return 对话轮次信息
     */
    Turn selectBySessionIdAndTid(@Param("sessionId") byte[] sessionId, @Param("tid") Integer tid);
    
    /**
     * 根据会话ID和父TID查询对话轮次列表
     * @param sessionId 会话ID
     * @param parentTid 父对话轮次序号
     * @return 对话轮次列表
     */
    List<Turn> selectBySessionIdAndParentTid(@Param("sessionId") byte[] sessionId, @Param("parentTid") Integer parentTid);
    
    /**
     * 根据会话ID分页查询对话轮次
     * @param sessionId 会话ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 对话轮次列表
     */
    List<Turn> selectBySessionIdWithPagination(@Param("sessionId") byte[] sessionId, 
                                               @Param("offset") int offset, 
                                               @Param("limit") int limit);
    
    /**
     * 根据会话ID查询对话轮次总数
     * @param sessionId 会话ID
     * @return 总数
     */
    int countBySessionId(@Param("sessionId") byte[] sessionId);
    
    /**
     * 更新对话轮次
     * @param turn 对话轮次信息
     * @return 影响行数
     */
    int update(Turn turn);
    
    /**
     * 删除对话轮次
     * @param id 对话轮次ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 根据会话ID查询所有对话轮次
     * @param sessionId 会话ID
     * @return 对话轮次列表
     */
    List<Turn> selectBySessionId(@Param("sessionId") byte[] sessionId);
    
    /**
     * 根据会话ID和父TID递归查询对话链
     * @param sessionId 会话ID
     * @param parentTid 父对话轮次ID
     * @return 对话轮次列表（按时间顺序）
     */
    List<Turn> selectConversationChain(@Param("sessionId") byte[] sessionId, @Param("parentTid") Integer parentTid);
    
    /**
     * 根据会话ID删除所有对话轮次
     * @param sessionId 会话ID
     * @return 影响行数
     */
    int deleteBySessionId(@Param("sessionId") byte[] sessionId);
}
