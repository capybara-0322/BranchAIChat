package com.example.ai.mapper;

import com.example.ai.entity.Session;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话数据访问层
 */
@Mapper
public interface SessionMapper {
    
    /**
     * 插入会话
     * @param session 会话信息
     * @return 影响行数
     */
    int insert(Session session);
    
    /**
     * 根据ID查询会话
     * @param id 会话ID
     * @return 会话信息
     */
    Session selectById(@Param("id") byte[] id);
    
    /**
     * 根据用户ID分页查询会话
     * @param uid 用户ID
     * @param title 标题关键字（可选）
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 会话列表
     */
    List<Session> selectByUserIdWithPagination(@Param("uid") Long uid, 
                                               @Param("title") String title,
                                               @Param("offset") int offset, 
                                               @Param("limit") int limit);
    
    /**
     * 根据用户ID和标题关键字查询会话总数
     * @param uid 用户ID
     * @param title 标题关键字（可选）
     * @return 总数
     */
    int countByUserIdAndTitle(@Param("uid") Long uid, @Param("title") String title);
    
    /**
     * 更新会话
     * @param session 会话信息
     * @return 影响行数
     */
    int update(Session session);
    
    /**
     * 删除会话
     * @param id 会话ID
     * @return 影响行数
     */
    int deleteById(@Param("id") byte[] id);
    
    /**
     * 根据用户ID查询所有会话
     * @param uid 用户ID
     * @return 会话列表
     */
    List<Session> selectByUserId(@Param("uid") Long uid);
}
