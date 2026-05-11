CREATE TABLE IF NOT EXISTS user_learning_profiles (
    user_id CHAR(36) NOT NULL,
    level_no INT NOT NULL DEFAULT 1,
    title VARCHAR(100) NOT NULL DEFAULT '입문 투자자',
    total_xp INT NOT NULL DEFAULT 0,
    xp_to_next_level INT NOT NULL DEFAULT 100,
    study_minutes INT NOT NULL DEFAULT 0,
    user_ranking INT NOT NULL DEFAULT 0,
    squad_ranking INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_learning_profiles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_courses (
    user_id CHAR(36) NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500) NOT NULL,
    level VARCHAR(30) NOT NULL DEFAULT 'beginner',
    keywords_json TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, course_id),
    CONSTRAINT fk_user_courses_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_courses_user_sort (user_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_lessons (
    user_id CHAR(36) NOT NULL,
    lesson_id VARCHAR(96) NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 0,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    completed_at DATETIME(6) NULL,
    study_minutes INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, lesson_id),
    CONSTRAINT fk_user_lessons_course FOREIGN KEY (user_id, course_id) REFERENCES user_courses(user_id, course_id) ON DELETE CASCADE,
    INDEX idx_user_lessons_user_course_sort (user_id, course_id, sort_order),
    INDEX idx_user_lessons_user_completed (user_id, completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
