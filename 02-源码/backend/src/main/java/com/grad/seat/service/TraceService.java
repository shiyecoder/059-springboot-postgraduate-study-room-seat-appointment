package com.grad.seat.service;

import com.grad.seat.common.UserContext;
import com.grad.seat.entity.SysMessage;
import com.grad.seat.entity.SysOperLog;
import com.grad.seat.mapper.SysMessageMapper;
import com.grad.seat.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 留痕服务：关键写操作写 sys_oper_log，状态流转与审批写 sys_message 通知。
 * 需求文档要求「更新业务表、写状态流水和站内消息」在同一事务内完成，这里被各业务服务直接调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceService {

    private final SysOperLogMapper logMapper;
    private final SysMessageMapper messageMapper;

    /** 关键操作留痕，失败不影响主流程 */
    public void log(String module, String operType, String bizNo, String content) {
        try {
            SysOperLog l = new SysOperLog();
            l.setModule(module);
            l.setOperType(operType);
            l.setBizNo(bizNo);
            l.setContent(content);
            l.setOperId(UserContext.userId());
            l.setOperName(UserContext.realName());
            l.setOperTime(LocalDateTime.now());
            l.setIp("10.20.1.1");
            l.setCreateTime(LocalDateTime.now());
            logMapper.insert(l);
        } catch (Exception e) {
            log.warn("写操作日志失败：{}", e.getMessage());
        }
    }

    /** 站内消息，msg_type 不能为空（DDL 未给默认值） */
    public void notify(Long receiverId, String title, String content, String msgType,
                       String bizType, Long bizId) {
        if (receiverId == null) {
            return;
        }
        try {
            SysMessage m = new SysMessage();
            m.setReceiverId(receiverId);
            m.setTitle(title);
            m.setContent(content);
            m.setMsgType(msgType == null ? "提醒" : msgType);
            m.setBizType(bizType);
            m.setBizId(bizId);
            m.setReadFlag(0);
            m.setCreateTime(LocalDateTime.now());
            m.setDeleted(0);
            messageMapper.insert(m);
        } catch (Exception e) {
            log.warn("发站内消息失败：{}", e.getMessage());
        }
    }
}
