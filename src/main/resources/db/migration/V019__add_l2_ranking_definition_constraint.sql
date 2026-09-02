-- V019: enforce one L2 ranking definition per school and ChallengeProject.

CREATE UNIQUE INDEX uq_ranking_def_l2_school_project
    ON ranking_definitions(school_id, project_id)
    WHERE layer = 'L2';
