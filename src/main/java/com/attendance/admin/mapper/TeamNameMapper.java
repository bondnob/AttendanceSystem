package com.attendance.admin.mapper;

import com.attendance.admin.model.TeamName;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TeamNameMapper {

    @Insert("""
            INSERT INTO team_name (org_unit_id, team_name, shift_category, sort_no, is_enabled)
            VALUES (#{orgUnitId}, #{teamName}, #{shiftCategory}, #{sortNo}, #{isEnabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeamName teamName);

    @Select("""
            SELECT id, org_unit_id, team_name, shift_category, sort_no, is_enabled, created_at, updated_at
            FROM team_name WHERE org_unit_id = #{orgUnitId} AND is_enabled = 1
            ORDER BY sort_no ASC, id ASC
            """)
    List<TeamName> findByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            <script>
            SELECT COUNT(1) FROM team_name
            <where>
                <if test="orgUnitId != null">
                    AND org_unit_id = #{orgUnitId}
                </if>
                <if test="teamName != null and teamName != ''">
                    AND team_name LIKE CONCAT('%', #{teamName}, '%')
                </if>
            </where>
            </script>
            """)
    Long countByCondition(@Param("orgUnitId") Long orgUnitId, @Param("teamName") String teamName);

    @Select("""
            <script>
            SELECT id, org_unit_id, team_name, shift_category, sort_no, is_enabled, created_at, updated_at
            FROM team_name
            <where>
                <if test="orgUnitId != null">
                    AND org_unit_id = #{orgUnitId}
                </if>
                <if test="teamName != null and teamName != ''">
                    AND team_name LIKE CONCAT('%', #{teamName}, '%')
                </if>
            </where>
            ORDER BY sort_no ASC, id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<TeamName> findPageByCondition(@Param("orgUnitId") Long orgUnitId,
                                       @Param("teamName") String teamName,
                                       @Param("offset") Integer offset,
                                       @Param("pageSize") Integer pageSize);

    @Select("""
            SELECT id, org_unit_id, team_name, shift_category, sort_no, is_enabled, created_at, updated_at
            FROM team_name WHERE id = #{id}
            """)
    TeamName findById(@Param("id") Long id);

    @Select("""
            SELECT id, org_unit_id, team_name, shift_category, sort_no, is_enabled, created_at, updated_at
            FROM team_name WHERE org_unit_id = #{orgUnitId} AND team_name = #{teamName}
            """)
    TeamName findByOrgUnitIdAndTeamName(@Param("orgUnitId") Long orgUnitId, @Param("teamName") String teamName);

    @Update("""
            UPDATE team_name
            SET org_unit_id = #{orgUnitId}, team_name = #{teamName}, shift_category = #{shiftCategory},
                sort_no = #{sortNo}, is_enabled = #{isEnabled}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(TeamName teamName);

    @Delete("""
            DELETE FROM team_name WHERE id = #{id}
            """)
    int deleteById(@Param("id") Long id);

    @Select("""
            SELECT MAX(sort_no) FROM team_name WHERE org_unit_id = #{orgUnitId}
            """)
    Integer findMaxSortNoByOrgUnitId(@Param("orgUnitId") Long orgUnitId);
}
