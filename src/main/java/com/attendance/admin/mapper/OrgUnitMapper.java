package com.attendance.admin.mapper;

import com.attendance.admin.model.OrgUnit;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrgUnitMapper {

    @Insert("""
            INSERT INTO org_unit (org_code, org_name, org_type, sort_no, is_enabled)
            VALUES (#{orgCode}, #{orgName}, #{orgType}, #{sortNo}, #{isEnabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrgUnit orgUnit);

    @Select("""
            SELECT id, org_code, org_name, org_type, sort_no, is_enabled
            FROM org_unit
            ORDER BY sort_no ASC, id ASC
            """)
    List<OrgUnit> findAll();

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM org_unit
            <if test="orgName != null and orgName != ''">
                WHERE org_name LIKE CONCAT('%', #{orgName}, '%')
            </if>
            </script>
            """)
    Long countByOrgName(@Param("orgName") String orgName);

    @Select("""
            <script>
            SELECT id, org_code, org_name, org_type, sort_no, is_enabled
            FROM org_unit
            <if test="orgName != null and orgName != ''">
                WHERE org_name LIKE CONCAT('%', #{orgName}, '%')
            </if>
            ORDER BY sort_no ASC, id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<OrgUnit> findPageByOrgName(@Param("orgName") String orgName,
                                    @Param("offset") Integer offset,
                                    @Param("pageSize") Integer pageSize);

    @Select("""
            SELECT id, org_code, org_name, org_type, sort_no, is_enabled
            FROM org_unit
            WHERE id = #{id}
            """)
    OrgUnit findById(@Param("id") Long id);

    @Select("""
            SELECT org_code
            FROM org_unit
            WHERE org_type = #{orgType}
            ORDER BY org_code DESC
            LIMIT 1
            """)
    String findLatestOrgCodeByType(@Param("orgType") String orgType);

    @Select("""
            SELECT MAX(sort_no)
            FROM org_unit
            """)
    Integer findMaxSortNo();

    @Update("""
            UPDATE org_unit
            SET org_code = #{orgCode},
                org_name = #{orgName},
                org_type = #{orgType},
                sort_no = #{sortNo},
                is_enabled = #{isEnabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(OrgUnit orgUnit);

    @Update("""
            UPDATE org_unit
            SET is_enabled = #{isEnabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateEnabled(@Param("id") Long id, @Param("isEnabled") Integer isEnabled);
}
