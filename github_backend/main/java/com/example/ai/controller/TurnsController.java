package com.example.ai.controller;

import com.example.ai.common.BaseController;
import com.example.ai.common.PageResult;
import com.example.ai.common.Result;
import com.example.ai.entity.Turn;
import com.example.ai.service.TurnService;
import com.example.ai.util.UuidUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * 对话轮次控制器
 * 负责处理对话轮次相关的HTTP请求
 */
@RestController
@RequestMapping("/api")
@com.example.ai.security.RequireAuth
public class TurnsController extends BaseController {

    @Autowired
    private TurnService turnService;

    /**
     * 创建对话轮次
     * @param request HTTP请求
     * @param sid 会话ID
     * @param body 请求体
     * @return 创建结果
     */
    @PostMapping("/v1/sessions/{sid}/turns")
    public Result<Map<String, Object>> createTurn(HttpServletRequest request, 
                                                  @PathVariable String sid, 
                                                  @RequestBody Map<String, Object> body) {
        logRequest(request, "创建对话轮次");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        Integer parentTid = body.get("parent_tid") == null ? 1 : (Integer) body.get("parent_tid");
        @SuppressWarnings("unchecked")
        Map<String, Object> userJson = (Map<String, Object>) body.get("user_json");
        @SuppressWarnings("unchecked")
        Map<String, Object> aiJson = (Map<String, Object>) body.get("ai_json");
        
        Turn turn = turnService.createTurn(userId, UUID.fromString(sid), parentTid,
                userJson == null ? null : toJson(userJson),
                aiJson == null ? null : toJson(aiJson));
        
        Map<String, Object> responseData = buildTurnResponse(sid, turn, true);
        return Result.success(responseData);
    }

    /**
     * 构建对话轮次响应数据
     * @param sid 会话ID
     * @param turn 对话轮次实体
     * @param includePayload 是否包含载荷数据
     * @return 响应数据
     */
    private Map<String, Object> buildTurnResponse(String sid, Turn turn, boolean includePayload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sid", sid);
        data.put("tid", turn.getTid());
        data.put("tuid", UuidUtils.bytesToUuid(turn.getTuid()).toString());
        data.put("parent_tid", turn.getParentTid());
        if (includePayload) {
            data.put("user_json", parseJsonOrNull(turn.getUserJson()));
            data.put("ai_json", parseJsonOrNull(turn.getAiJson()));
        }
        data.put("created_at", Instant.ofEpochSecond(turn.getCreatedAt()).toString());
        data.put("last_accessed_at", Instant.ofEpochSecond(turn.getLastAccessedAt()).toString());
        
        // 添加高度信息
        data.put("height", turn.getHeight() != null ? turn.getHeight() : 0);
        
        // 获取子节点ID列表
        List<Integer> childrenTids = getChildrenTids(sid, turn.getTid());
        data.put("children_tids", childrenTids);
        
        return data;
    }

    /**
     * 获取子节点ID列表
     * @param sid 会话ID
     * @param tid 节点ID
     * @return 子节点ID列表
     */
    private List<Integer> getChildrenTids(String sid, Integer tid) {
        try {
            List<Turn> children = turnService.getChildren(0L, UUID.fromString(sid), tid, false);
            List<Integer> childrenTids = new ArrayList<>();
            for (Turn child : children) {
                childrenTids.add(child.getTid());
            }
            return childrenTids;
        } catch (Exception e) {
            logger.warn("获取子节点ID列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取单个对话轮次
     * @param request HTTP请求
     * @param sid 会话ID
     * @param tid 对话轮次ID
     * @param includeChildren 是否包含子节点
     * @param includePayload 是否包含载荷数据
     * @return 对话轮次详情
     */
    @GetMapping("/v1/sessions/{sid}/turns/{tid}")
    public Result<Map<String, Object>> getTurn(HttpServletRequest request, 
                                               @PathVariable String sid, 
                                               @PathVariable Integer tid,
                                               @RequestParam(required = false, defaultValue = "false") boolean include_children,
                                               @RequestParam(required = false, defaultValue = "true") boolean include_payload) {
        logRequest(request, "获取对话轮次详情");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        return turnService.getTurn(UUID.fromString(sid), tid)
                .map(turn -> Result.success(buildTurnResponse(sid, turn, include_payload)))
                .orElseGet(() -> Result.paramError("节点不存在"));
    }

    /**
     * 分页查询对话轮次
     * @param request HTTP请求
     * @param sid 会话ID
     * @param page 页码
     * @param pageSize 每页大小
     * @param orderBy 排序字段
     * @param desc 是否降序
     * @param includePayload 是否包含载荷数据
     * @return 分页结果
     */
    @GetMapping("/v1/sessions/{sid}/turns/page")
    public Result<Map<String, Object>> pageTurns(HttpServletRequest request, 
                                                 @PathVariable String sid,
                                                 @RequestParam int page, 
                                                 @RequestParam int pageSize,
                                                 @RequestParam(required = false, defaultValue = "created_at") String orderBy,
                                                 @RequestParam(required = false, defaultValue = "true") boolean desc,
                                                 @RequestParam(required = false, defaultValue = "false") boolean includePayload) {
        logRequest(request, "分页查询对话轮次");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        PageResult<Turn> pageResult = turnService.pageTurns(UUID.fromString(sid), page, pageSize);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", pageResult.getTotalElements());
        
        List<Map<String, Object>> records = new ArrayList<>();
        for (Turn turn : pageResult.getContent()) {
            records.add(buildTurnResponse(sid, turn, includePayload));
        }
        response.put("records", records);
        
        return Result.success(response);
    }

    /**
     * 删除对话轮次
     * @param request HTTP请求
     * @param sid 会话ID
     * @param tid 对话轮次ID
     * @param mode 删除模式
     * @return 删除结果
     */
    @DeleteMapping("/v1/sessions/{sid}/turns/{tid}")
    public Result<Void> deleteTurn(HttpServletRequest request, 
                                  @PathVariable String sid, 
                                  @PathVariable Integer tid,
                                  @RequestParam(required = false, defaultValue = "subtree") String mode) {
        logRequest(request, "删除对话轮次");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        boolean subtree = !"node".equalsIgnoreCase(mode);
        boolean success = turnService.deleteTurn(userId, UUID.fromString(sid), tid, subtree);
        
        return success ? Result.success(null) : Result.paramError("节点不存在");
    }

    /**
     * 根据TUID解析对话轮次
     * @param request HTTP请求
     * @param tuid 对话轮次唯一标识
     * @return 解析结果
     */
    @GetMapping("/v1/turns/resolve/{tuid}")
    public Result<Map<String, Object>> resolveTurn(HttpServletRequest request, @PathVariable String tuid) {
        logRequest(request, "解析对话轮次");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        return turnService.getByTuid(UUID.fromString(tuid))
                .map(turn -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("sid", UuidUtils.bytesToUuid(turn.getSessionId()).toString());
                    data.put("tid", turn.getTid());
                    return Result.success(data);
                })
                .orElseGet(() -> Result.paramError("tuid不存在"));
    }

    /**
     * 将Map转换为JSON字符串
     * @param map 要转换的Map
     * @return JSON字符串
     */
    private static String toJson(Map<String, Object> map) {
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(map).toString();
    }

    /**
     * 加载所有节点高度到Redis
     * @param request HTTP请求
     * @param sid 会话ID
     * @return 操作结果
     */
    @PostMapping("/v1/sessions/{sid}/turns/load-heights")
    public Result<Void> loadHeights(HttpServletRequest request, @PathVariable String sid) {
        logRequest(request, "加载节点高度信息到Redis");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        try {
            turnService.loadHeightsToRedis(userId, UUID.fromString(sid));
            return Result.success(null);
        } catch (Exception e) {
            logger.error("加载节点高度信息失败: {}", e.getMessage(), e);
            return Result.error(5000, "加载节点高度信息失败");
        }
    }

    /**
     * 获取指定节点及父链的高度
     * @param request HTTP请求
     * @param sid 会话ID
     * @param tid 节点ID
     * @return 节点高度信息列表
     */
    @GetMapping("/v1/sessions/{sid}/turns/{tid}/heights")
    public Result<List<Map<String, Object>>> getNodeHeights(HttpServletRequest request, 
                                                           @PathVariable String sid, 
                                                           @PathVariable Integer tid) {
        logRequest(request, "获取节点高度信息");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        try {
            List<Map<String, Object>> heights = turnService.getNodeHeights(userId, UUID.fromString(sid), tid);
            return Result.success(heights);
        } catch (Exception e) {
            logger.error("获取节点高度信息失败: {}", e.getMessage(), e);
            return Result.error(5000, "获取节点高度信息失败");
        }
    }

    /**
     * 获取指定节点的所有子节点
     * @param request HTTP请求
     * @param sid 会话ID
     * @param tid 节点ID
     * @param includePayload 是否包含载荷数据
     * @return 子节点列表
     */
    @GetMapping("/v1/sessions/{sid}/turns/{tid}/children")
    public Result<List<Map<String, Object>>> getChildren(HttpServletRequest request, 
                                                        @PathVariable String sid, 
                                                        @PathVariable Integer tid,
                                                        @RequestParam(required = false, defaultValue = "false") boolean include_payload) {
        logRequest(request, "获取子节点");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        try {
            List<Turn> children = turnService.getChildren(userId, UUID.fromString(sid), tid, include_payload);
            
            // 转换为响应格式
            List<Map<String, Object>> result = new ArrayList<>();
            for (Turn child : children) {
                Map<String, Object> childData = buildChildResponse(sid, child, include_payload);
                result.add(childData);
            }
            
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取子节点失败: {}", e.getMessage(), e);
            return Result.error(5000, "获取子节点失败");
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
        
        // 添加高度信息
        data.put("height", turn.getHeight() != null ? turn.getHeight() : 0);
        
        // 获取子节点的子节点ID列表
        List<Integer> childrenTids = getChildrenTids(sid, turn.getTid());
        data.put("children_tids", childrenTids);
        
        return data;
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
