package com.yotouch.base.bizentity;

import java.util.List;

import com.yotouch.core.workflow.AfterActionHandler;
import com.yotouch.core.workflow.BeforeActionHandler;
import com.yotouch.core.workflow.CanDoActionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yotouch.core.Consts;
import com.yotouch.core.entity.Entity;
import com.yotouch.core.entity.MetaEntity;
import com.yotouch.core.runtime.DbSession;
import com.yotouch.core.workflow.Workflow;
import com.yotouch.core.workflow.WorkflowAction;
import com.yotouch.core.workflow.WorkflowException;
import com.yotouch.core.workflow.WorkflowState;

@Service
public class BizEntityServiceImpl implements BizEntityService {
    
    @Autowired
    private BizEntityManager beMgr;

    @Override
    public BizEntity prepareWorkflow(BizMetaEntity bme) {
        
        MetaEntity me = bme.getMetaEntity();
        Entity entity = me.newEntity();
        entity.setValue(Consts.BIZ_ENTITY_FIELD_WORKFLOW, bme.getWorkflow().getName());
        entity.setValue(Consts.BIZ_ENTITY_FIELD_STATE, "");
        
        BizEntity be = this.convert(entity);
        
        return be;
    }

    @Override
    public BizEntity convert(Entity entity) {
        
        BizMetaEntity bme = this.beMgr.getBizMetaEntityByEntity(entity.getMetaEntity().getName());
        
        BizEntityImpl bei = new BizEntityImpl(bme, entity);
        
        return bei;
    }

    @Override
    public BizEntity doAction(DbSession dbSession, String actionName, BizEntity bizEntity) {
        return this.doAction(dbSession, actionName, bizEntity.getEntity());
    }

    @Override
    public BizEntity doAction(DbSession dbSession, String actionName, Entity entity) {

        WorkflowAction wfa = checkWorkflowAndGetAction(actionName, entity);
        
        entity.setValue(Consts.BIZ_ENTITY_FIELD_STATE, wfa.getTo().getName());
        entity = dbSession.save(entity);
        return this.convert(entity);
    }

    private WorkflowAction checkWorkflowAndGetAction(String actionName, Entity entity) {
        BizMetaEntity bme = this.beMgr.getBizMetaEntityByEntity(entity.getMetaEntity().getName());

        if (bme == null) {
            throw new WorkflowException("No such workflow for MetaEntity [ "+entity.getMetaEntity().getName()+"]");
        }

        Workflow wf = bme.getWorkflow();

        String stateName = entity.v(Consts.BIZ_ENTITY_FIELD_STATE);
        WorkflowState wfState = null;
        if (StringUtils.isEmpty(stateName)) {
            wfState = wf.getStartState();
        } else {
            wfState = wf.getState(stateName);
        }

        if (wfState == null) {
            throw new WorkflowException("No such state [" + entity.v(Consts.BIZ_ENTITY_FIELD_STATE) + "] for workfow [ " + wf.getName() + "]");
        }

        List<WorkflowAction> actList = wfState.getOutActions();
        WorkflowAction wfa = null;

        for (WorkflowAction act: actList) {
            if (act.getName().equalsIgnoreCase(actionName)) {
                wfa = act;
                break;
            }
        }

        if (wfa == null) {
            throw new WorkflowException("No such action [" + actionName + "] for workflow [" + wf.getName() + "]");
        }
        return wfa;
    }

    @Override
    public BizEntity doAction(DbSession dbSession, String actionName, Entity entity, BeforeActionHandler beforeActionHandler, AfterActionHandler afterActionHandler) throws WorkflowException {

        TransitResult tr = doTransit(dbSession, actionName, entity, beforeActionHandler);
        entity = tr.entity;
        WorkflowAction wfa = tr.wfa;

        afterActionHandler.doAfterAction(dbSession, wfa, entity);

        return this.convert(entity);
    }

    @Override
    public boolean canDoAction(DbSession dbSession, WorkflowAction wa, Entity entity, CanDoActionHandler canDoActionHandler) {
        return canDoActionHandler.canDoAction(dbSession, wa, entity);
    }

    @Transactional
    private TransitResult doTransit(DbSession dbSession, String actionName, Entity entity, BeforeActionHandler beforeActionHandler) {
        WorkflowAction wfa = checkWorkflowAndGetAction(actionName, entity);
        beforeActionHandler.doBeforeAction(dbSession, wfa, entity);

        entity.setValue(Consts.BIZ_ENTITY_FIELD_STATE, wfa.getTo().getName());
        entity = dbSession.save(entity);

        Entity wfaLog = dbSession.newEntity("workflowActionLog");
        wfaLog.setValue("action", actionName);
        wfaLog.setValue("workflow", wfa.getWorkflow().getName());
        wfaLog.setValue("entityUuid", entity.getUuid());
        dbSession.save(wfaLog);

        return new TransitResult(wfa, entity);
    }

    class TransitResult {
        WorkflowAction wfa;
        Entity entity;

        public TransitResult(WorkflowAction wfa, Entity entity) {
            this.wfa = wfa;
            this.entity = entity;
        }
    }


}
