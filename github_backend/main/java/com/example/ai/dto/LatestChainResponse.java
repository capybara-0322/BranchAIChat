package com.example.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 最近活跃节点路径链响应DTO
 */
public class LatestChainResponse {
    
    @JsonProperty("sid")
    private String sid;
    
    @JsonProperty("path_tid_list")
    private List<Long> pathTidList;
    
    @JsonProperty("nodes")
    private List<ChainNode> nodes;
    
    public LatestChainResponse() {}
    
    public LatestChainResponse(String sid, List<Long> pathTidList, List<ChainNode> nodes) {
        this.sid = sid;
        this.pathTidList = pathTidList;
        this.nodes = nodes;
    }
    
    public String getSid() {
        return sid;
    }
    
    public void setSid(String sid) {
        this.sid = sid;
    }
    
    public List<Long> getPathTidList() {
        return pathTidList;
    }
    
    public void setPathTidList(List<Long> pathTidList) {
        this.pathTidList = pathTidList;
    }
    
    public List<ChainNode> getNodes() {
        return nodes;
    }
    
    public void setNodes(List<ChainNode> nodes) {
        this.nodes = nodes;
    }
    
    /**
     * 路径链节点
     */
    public static class ChainNode {
        
        @JsonProperty("tid")
        private Long tid;
        
        @JsonProperty("tuid")
        private String tuid;
        
        @JsonProperty("parent_tid")
        private Long parentTid;
        
        @JsonProperty("user_json")
        private Object userJson;
        
        @JsonProperty("ai_json")
        private Object aiJson;
        
        @JsonProperty("height")
        private Integer height;
        
        public ChainNode() {}
        
        public ChainNode(Long tid, String tuid, Long parentTid, Object userJson, Object aiJson) {
            this.tid = tid;
            this.tuid = tuid;
            this.parentTid = parentTid;
            this.userJson = userJson;
            this.aiJson = aiJson;
        }
        
        public ChainNode(Long tid, String tuid, Long parentTid, Object userJson, Object aiJson, Integer height) {
            this.tid = tid;
            this.tuid = tuid;
            this.parentTid = parentTid;
            this.userJson = userJson;
            this.aiJson = aiJson;
            this.height = height;
        }
        
        public Long getTid() {
            return tid;
        }
        
        public void setTid(Long tid) {
            this.tid = tid;
        }
        
        public String getTuid() {
            return tuid;
        }
        
        public void setTuid(String tuid) {
            this.tuid = tuid;
        }
        
        public Long getParentTid() {
            return parentTid;
        }
        
        public void setParentTid(Long parentTid) {
            this.parentTid = parentTid;
        }
        
        public Object getUserJson() {
            return userJson;
        }
        
        public void setUserJson(Object userJson) {
            this.userJson = userJson;
        }
        
        public Object getAiJson() {
            return aiJson;
        }
        
        public void setAiJson(Object aiJson) {
            this.aiJson = aiJson;
        }
        
        public Integer getHeight() {
            return height;
        }
        
        public void setHeight(Integer height) {
            this.height = height;
        }
    }
}
