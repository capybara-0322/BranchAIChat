package com.example.ai.controller;

import com.example.ai.common.BaseController;
import com.example.ai.common.PageResult;
import com.example.ai.common.Result;
import com.example.ai.dto.LatestChainResponse;
import com.example.ai.entity.Session;
import com.example.ai.service.SessionService;
import com.example.ai.util.UuidUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * 会话控制器
 * 负责处理会话相关的HTTP请求
 */
@RestController
@RequestMapping("/api")
@com.example.ai.security.RequireAuth
public class SessionsController extends BaseController {

    @Autowired
    private SessionService sessionService;

    /**
     * 创建会话
     * @param request HTTP请求
     * @param body 请求体
     * @return 创建结果
     */
    @PostMapping("/sessions/create")
    public Result<Map<String, Object>> createSession(HttpServletRequest request, 
                                                     @RequestBody(required = false) Map<String, Object> body) {
        logRequest(request, "创建会话");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        String title = body == null ? null : (String) body.get("title");
        Session session = sessionService.createSession(userId, title);
        
        Map<String, Object> responseData = buildSessionResponse(session);
        return Result.success("创建成功", responseData);
    }

    /**
     * 构建会话响应数据
     * @param session 会话实体
     * @return 响应数据
     */
    private Map<String, Object> buildSessionResponse(Session session) {
        Map<String, Object> data = new LinkedHashMap<>();
        UUID sid = UuidUtils.bytesToUuid(session.getId());
        data.put("sid", sid.toString());
        data.put("title", session.getTitle());
        data.put("created_at", Instant.ofEpochSecond(session.getCreatedAt()).toString());
        data.put("updated_at", Instant.ofEpochSecond(session.getUpdatedAt()).toString());
        data.put("last_active_tid", session.getLastActiveTid());
        data.put("last_active_tuid", session.getLastActiveTuid() == null ? null : 
                UuidUtils.bytesToUuid(session.getLastActiveTuid()).toString());
        data.put("turn_count", session.getTurnSeq());
        return data;
    }

    /**
     * 分页查询会话
     * @param request HTTP请求
     * @param title 标题关键字
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @GetMapping("/v1/sessions/page")
    public Result<Map<String, Object>> pageSessions(HttpServletRequest request,
                                                    @RequestParam(required = false) String title,
                                                    @RequestParam int page,
                                                    @RequestParam int pageSize) {
        logRequest(request, "分页查询会话");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        PageResult<Session> pageResult = sessionService.pageSessions(userId, title, page, pageSize);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", pageResult.getTotalElements());
        
        List<Map<String, Object>> records = new ArrayList<>();
        for (Session session : pageResult.getContent()) {
            records.add(buildSessionResponse(session));
        }
        response.put("records", records);
        
        return Result.success(response);
    }

    /**
     * 获取单个会话详情
     * @param request HTTP请求
     * @param sid 会话ID
     * @return 会话详情
     */
    @GetMapping("/v1/sessions/{sid}")
    public Result<Map<String, Object>> getSession(HttpServletRequest request, @PathVariable String sid) {
        logRequest(request, "获取会话详情");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        UUID sessionId = UUID.fromString(sid);
        return sessionService.getBySid(sessionId)
                .map(session -> {
                    Map<String, Object> data = buildSessionResponse(session);
                    return Result.success(data);
                })
                .orElseGet(() -> Result.paramError("会话不存在"));
    }

    /**
     * 更新会话信息
     * @param request HTTP请求
     * @param sid 会话ID
     * @param body 更新数据
     * @return 更新结果
     */
    @PatchMapping("/v1/sessions/{sid}")
    public Result<Map<String, Object>> updateSession(HttpServletRequest request, 
                                                     @PathVariable String sid, 
                                                     @RequestBody Map<String, Object> body) {
        logRequest(request, "更新会话信息");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        String title = (String) body.get("title");
        Integer lastActiveTid = (Integer) body.get("last_active_tid");
        String lastActiveTuidStr = (String) body.get("last_active_tuid");
        UUID lastActiveTuid = lastActiveTuidStr == null ? null : UUID.fromString(lastActiveTuidStr);
        UUID sessionId = UUID.fromString(sid);
        
        return sessionService.updateSession(sessionId, title, lastActiveTid, lastActiveTuid)
                .map(session -> {
                    Map<String, Object> data = buildSessionResponse(session);
                    return Result.success(data);
                })
                .orElseGet(() -> Result.paramError("会话不存在"));
    }

    /**
     * 删除会话
     * @param request HTTP请求
     * @param sid 会话ID
     * @return 删除结果
     */
    @DeleteMapping("/v1/sessions/{sid}")
    public Result<Void> deleteSession(HttpServletRequest request, @PathVariable String sid) {
        logRequest(request, "删除会话");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        boolean success = sessionService.deleteSession(UUID.fromString(sid));
        return success ? Result.success(null) : Result.paramError("会话不存在");
    }

    /**
     * 激活会话
     * @param request HTTP请求
     * @param sid 会话ID
     * @param body 激活数据
     * @return 激活结果
     */
    @PostMapping("/v1/sessions/{sid}/active")
    public Result<Void> activateSession(HttpServletRequest request, 
                                       @PathVariable String sid, 
                                       @RequestBody Map<String, Object> body) {
        logRequest(request, "激活会话");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        Integer tid = (Integer) body.get("tid");
        String tuidStr = (String) body.get("tuid");
        UUID tuid = tuidStr == null ? null : UUID.fromString(tuidStr);
        
        sessionService.updateSession(UUID.fromString(sid), null, tid, tuid);
        return Result.success(null);
    }

    /**
     * 获取最近活跃节点的路径链
     * @param request HTTP请求
     * @param sid 会话ID
     * @param includePayload 是否包含payload
     * @return 路径链响应
     */
    @GetMapping("/v1/sessions/{sid}/latest-chain")
    public Result<LatestChainResponse> getLatestChain(HttpServletRequest request,
                                                      @PathVariable String sid,
                                                      @RequestParam(defaultValue = "true") boolean includePayload) {
        logRequest(request, "获取最近活跃节点路径链");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) return Result.unauthorized();
        
        UUID sessionId = UUID.fromString(sid);
        return sessionService.getLatestChain(userId, sessionId, includePayload)
                .map(Result::success)
                .orElseGet(() -> Result.paramError("会话不存在或没有活跃的对话轮次"));
    }
}
