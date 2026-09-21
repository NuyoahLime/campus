package com.campusguinness.result.application.format;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultFormatEditPersistenceIT extends PostgreSqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    UUID user, school, activityA, activityB, resultA, resultB, versionA, versionB, recordA, recordB;

    @BeforeEach void setUp() {
        user=UUID.randomUUID(); school=UUID.randomUUID(); activityA=UUID.randomUUID(); activityB=UUID.randomUUID();
        resultA=UUID.randomUUID(); resultB=UUID.randomUUID(); versionA=UUID.randomUUID(); versionB=UUID.randomUUID();
        recordA=UUID.randomUUID(); recordB=UUID.randomUUID(); String s=UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",user,"fmtdb-"+s,"x","NORMAL");
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",school,"S","USCC","U"+s,"I"+s,"PRIMARY","R","A","C","13800000000","a@b.c","NORMAL");
        for(UUID a: new UUID[]{activityA,activityB}) jdbc.update("INSERT INTO activities(id,school_id,title,execution_status,public_status,created_by) VALUES (?,?,?,?,?,?)",a,school,"A","PUBLISHED","PUBLIC",user);
        jdbc.update("INSERT INTO activity_results(id,school_id,activity_id) VALUES (?,?,?)",resultA,school,activityA);
        jdbc.update("INSERT INTO activity_results(id,school_id,activity_id) VALUES (?,?,?)",resultB,school,activityB);
        version(versionA,resultA); version(versionB,resultB);
        record(recordA,resultA,versionA,1,"ok"); record(recordB,resultB,versionB,1,"ok");
    }
    @AfterEach void clean(){ jdbc.update("DELETE FROM result_format_heads"); jdbc.update("DELETE FROM result_format_edit_records"); jdbc.update("UPDATE activity_results SET current_candidate_version_id=NULL,current_internal_version_id=NULL,current_public_version_id=NULL WHERE school_id=?",school); jdbc.update("DELETE FROM result_versions WHERE result_id IN (?,?)",resultA,resultB); jdbc.update("DELETE FROM activity_results WHERE school_id=?",school); jdbc.update("DELETE FROM activities WHERE school_id=?",school); jdbc.update("DELETE FROM schools WHERE id=?",school); jdbc.update("DELETE FROM users WHERE id=?",user); }

    @Test void db01MissingResultRejected(){ reject(() -> record(UUID.randomUUID(),UUID.randomUUID(),versionA,2,"x")); }
    @Test void db02WrongResultVersionPairRejected(){ reject(() -> record(UUID.randomUUID(),resultB,versionA,2,"x")); }
    @Test void db03DuplicateRevisionRejected(){ reject(() -> record(UUID.randomUUID(),resultA,versionA,1,"x")); }
    @Test void db04OneHeadPerVersion(){ head(versionA,resultA,recordA); reject(() -> head(versionA,resultA,recordA)); }
    @Test void db05HeadWrongResultVersionPair(){ reject(() -> head(versionA,resultB,recordA)); }
    @Test void db06HeadRecordFromOtherVersion(){ reject(() -> head(versionA,resultA,recordB)); }
    @Test void db07HeadRecordFromOtherResult(){ reject(() -> head(versionB,resultB,recordA)); }
    @Test void db08MissingEditorRejected(){ reject(() -> jdbc.update("INSERT INTO result_format_edit_records(id,result_id,result_version_id,revision,payload,reason,edited_by) VALUES (?,?,?,?,?::jsonb,?,?)",UUID.randomUUID(),resultA,versionA,2,"{}","x",UUID.randomUUID())); }
    @Test void db09BlankReasonRejected(){ reject(() -> record(UUID.randomUUID(),resultA,versionA,2,"   ")); }
    @Test void db10LongReasonRejected(){ reject(() -> record(UUID.randomUUID(),resultA,versionA,2,"x".repeat(2001))); }
    @Test void db11ZeroRevisionRejected(){ reject(() -> jdbc.update("INSERT INTO result_format_edit_records(id,result_id,result_version_id,revision,payload,reason,edited_by) VALUES (?,?,?,?,?::jsonb,?,?)",UUID.randomUUID(),resultA,versionA,0,"{}","x",user)); }
    @Test void db12NonObjectPayloadRejected(){ reject(() -> jdbc.update("INSERT INTO result_format_edit_records(id,result_id,result_version_id,revision,payload,reason,edited_by) VALUES (?,?,?,?,?::jsonb,?,?)",UUID.randomUUID(),resultA,versionA,2,"[]","x",user)); }
    @Test void db13ReferencedVersionDeleteRestricted(){ reject(() -> jdbc.update("DELETE FROM result_versions WHERE id=?",versionA)); }
    @Test void db14HeadRecordDeleteRestricted(){ head(versionA,resultA,recordA); reject(() -> jdbc.update("DELETE FROM result_format_edit_records WHERE id=?",recordA)); }

    void version(UUID id,UUID result){jdbc.update("INSERT INTO result_versions(id,result_id,version_number,title,summary_text,score_highlights,media_refs,is_core_content_modified) VALUES (?,?,?,?,?,?::jsonb,?::jsonb,false)",id,result,1,"t","abc","[]","[]");}
    void record(UUID id,UUID result,UUID version,int revision,String reason){jdbc.update("INSERT INTO result_format_edit_records(id,result_id,result_version_id,revision,payload,reason,edited_by) VALUES (?,?,?,?,?::jsonb,?,?)",id,result,version,revision,"{}",reason,user);}
    void head(UUID version,UUID result,UUID record){jdbc.update("INSERT INTO result_format_heads(result_version_id,result_id,current_format_edit_record_id,version) VALUES (?,?,?,1)",version,result,record);}
    void reject(Runnable action){assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class);}
}
