package depth.finvibe.gamification.modules.study.application.service;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.persistence.learning.UserChallengeProgressEntity;
import depth.finvibe.shared.persistence.learning.UserChallengeProgressId;
import depth.finvibe.shared.persistence.learning.UserChallengeProgressRepository;
import depth.finvibe.shared.persistence.learning.UserCourseEntity;
import depth.finvibe.shared.persistence.learning.UserCourseId;
import depth.finvibe.shared.persistence.learning.UserCourseRepository;
import depth.finvibe.shared.persistence.learning.UserLearningProfileEntity;
import depth.finvibe.shared.persistence.learning.UserLearningProfileRepository;
import depth.finvibe.shared.persistence.learning.UserLessonEntity;
import depth.finvibe.shared.persistence.learning.UserLessonId;
import depth.finvibe.shared.persistence.learning.UserLessonRepository;
import depth.finvibe.shared.persistence.learning.UserSquadMembershipEntity;
import depth.finvibe.shared.persistence.learning.UserSquadMembershipRepository;
import depth.finvibe.shared.persistence.market.FavoriteStockRepository;
import depth.finvibe.shared.persistence.user.UserEntity;
import depth.finvibe.shared.persistence.user.UserRepository;
import depth.finvibe.shared.ranking.UserProfitSnapshotDailyService;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.FinvibeUtils;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.TimeUtil;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {
    private static final int XP_PER_COMPLETED_LESSON = 50;
    private static final int XP_PER_LEVEL = 100;

    private final UserLearningProfileRepository profileRepository;
    private final UserCourseRepository courseRepository;
    private final UserLessonRepository lessonRepository;
    private final UserChallengeProgressRepository challengeProgressRepository;
    private final UserSquadMembershipRepository squadMembershipRepository;
    private final FavoriteStockRepository favoriteStockRepository;
    private final UserRepository userRepository;
    private final UserProfitSnapshotDailyService profitSnapshotDailyService;
    private final AppState appState;

    public LearningService(
            UserLearningProfileRepository profileRepository,
            UserCourseRepository courseRepository,
            UserLessonRepository lessonRepository,
            UserChallengeProgressRepository challengeProgressRepository,
            UserSquadMembershipRepository squadMembershipRepository,
            FavoriteStockRepository favoriteStockRepository,
            UserRepository userRepository,
            UserProfitSnapshotDailyService profitSnapshotDailyService,
            AppState appState
    ) {
        this.profileRepository = profileRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.challengeProgressRepository = challengeProgressRepository;
        this.squadMembershipRepository = squadMembershipRepository;
        this.favoriteStockRepository = favoriteStockRepository;
        this.userRepository = userRepository;
        this.profitSnapshotDailyService = profitSnapshotDailyService;
        this.appState = appState;
    }

    @Transactional
    public Map<String, Object> learningDashboard(String userId) {
        ensureLearningInitialized(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedAt", TimeUtil.nowSeoulIso());
        result.put("aiInsight", appState.aiRecommendationToday());
        result.put("courses", listCourses(userId));
        result.put("badges", listBadges(userId));
        result.put("recommendedKeywords", appState.recommendedKeywordsList());
        result.put("stats", metricsMe(userId));
        result.put("weeklyGoal", weeklyGoalFor(userId));
        result.put("xp", xpMe(userId));
        result.put("challenges", listChallenges(userId));
        result.put("completedChallenges", completedChallenges(userId));
        result.put("squad", squadMe(userId));
        return result;
    }

    @Transactional
    public List<Map<String, Object>> listCourses(String userId) {
        ensureLearningInitialized(userId);
        List<UserCourseEntity> courses = courseRepository.findAllByIdUserIdOrderBySortOrderAsc(userId);
        Map<String, List<UserLessonEntity>> lessonsByCourse = lessonRepository.findAllByIdUserIdOrderByCourseIdAscSortOrderAsc(userId).stream()
                .collect(Collectors.groupingBy(UserLessonEntity::getCourseId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserCourseEntity course : courses) {
            rows.add(toCourseMap(course, lessonsByCourse.getOrDefault(course.getId().getCourseId(), List.of())));
        }
        return rows;
    }

    public Map<String, Object> previewCourse(Map<String, Object> payload) {
        List<String> keywords = toStringList(payload.get("keywords"));
        String courseName = Maps.str(payload, "courseName");
        String level = FinvibeUtils.inferCourseLevel(keywords);
        List<String> suggestions = new ArrayList<>();
        suggestions.add((keywords.isEmpty() ? "투자" : keywords.get(0)) + " 기초 개념 이해하기");
        suggestions.add("관련 차트 분석 및 지표 활용법");
        suggestions.add("실전 투자 전략 수립하기");
        suggestions.add("포트폴리오 구성 및 리스크 관리");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", courseName);
        result.put("keywords", keywords);
        result.put("suggestedLessons", suggestions);
        result.put("inferredLevel", level);
        return result;
    }

    @Transactional
    public Map<String, Object> createCourse(String userId, Map<String, Object> payload) {
        ensureLearningInitialized(userId);
        Map<String, Object> preview = previewCourse(payload);
        List<String> keywords = toStringList(payload.get("keywords"));
        List<String> suggestedLessons = toStringList(preview.get("suggestedLessons"));
        String courseId = "custom-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int sortOrder = nextCourseSortOrder(userId);

        UserCourseEntity course = new UserCourseEntity();
        course.setId(new UserCourseId(userId, courseId));
        course.setTitle(Maps.str(payload, "courseName"));
        course.setDescription(String.join(", ", keywords.subList(0, Math.min(3, keywords.size()))) + " 중심 AI 맞춤 학습 코스");
        course.setLevel(String.valueOf(preview.get("inferredLevel")));
        course.setKeywordsJson(Json.stringify(keywords));
        course.setSortOrder(sortOrder);
        courseRepository.save(course);

        for (int i = 0; i < suggestedLessons.size(); i++) {
            UserLessonEntity lesson = new UserLessonEntity();
            lesson.setId(new UserLessonId(userId, courseId + "-lesson-" + (i + 1)));
            lesson.setCourseId(courseId);
            lesson.setTitle(suggestedLessons.get(i));
            lesson.setDurationMinutes(6 + (i + 1));
            lesson.setCompleted(false);
            lesson.setCompletedAt(null);
            lesson.setStudyMinutes(0);
            lesson.setSortOrder(i + 1);
            lessonRepository.save(lesson);
        }
        return toCourseMap(course, lessonRepository.findAllByIdUserIdAndCourseIdOrderBySortOrderAsc(userId, courseId));
    }

    @Transactional
    public Map<String, Object> getLesson(String userId, String lessonId) {
        ensureLearningInitialized(userId);
        UserLessonEntity lesson = requireLesson(userId, lessonId);
        UserCourseEntity course = requireCourse(userId, lesson.getCourseId());
        return toLessonMap(course, lesson, true);
    }

    @Transactional
    public Map<String, Object> completeLesson(String userId, String lessonId) {
        ensureLearningInitialized(userId);
        UserLessonEntity lesson = requireLesson(userId, lessonId);
        UserCourseEntity course = requireCourse(userId, lesson.getCourseId());
        UserLearningProfileEntity profile = requireProfile(userId);

        if (!lesson.isCompleted()) {
            lesson.setCompleted(true);
            lesson.setCompletedAt(LocalDateTime.now(TimeUtil.SEOUL));
            lessonRepository.save(lesson);

            profile.setStudyMinutes(profile.getStudyMinutes() + Math.max(lesson.getDurationMinutes(), 0));
            profile.setTotalXp(profile.getTotalXp() + XP_PER_COMPLETED_LESSON);
            applyXpLevel(profile);
            profileRepository.save(profile);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lesson", toLessonMap(course, lesson, false));
        result.put("course", toCourseMap(course, lessonRepository.findAllByIdUserIdAndCourseIdOrderBySortOrderAsc(userId, course.getId().getCourseId())));
        result.put("stats", metricsMe(userId));
        result.put("xp", xpMe(userId));
        return result;
    }

    @Transactional
    public List<Map<String, Object>> lessonCompletions(String userId) {
        ensureLearningInitialized(userId);
        Map<String, UserCourseEntity> courses = courseRepository.findAllByIdUserIdOrderBySortOrderAsc(userId).stream()
                .collect(Collectors.toMap(course -> course.getId().getCourseId(), course -> course));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserLessonEntity lesson : lessonRepository.findAllByIdUserIdOrderByCourseIdAscSortOrderAsc(userId)) {
            if (!lesson.isCompleted()) {
                continue;
            }
            UserCourseEntity course = courses.get(lesson.getCourseId());
            rows.add(Maps.of(
                    "courseId", lesson.getCourseId(),
                    "lessonId", lesson.getId().getLessonId(),
                    "title", lesson.getTitle(),
                    "courseTitle", course == null ? null : course.getTitle(),
                    "completedAt", lesson.getCompletedAt() == null ? null : lesson.getCompletedAt().atOffset(ZoneOffset.ofHours(9)).toString()
            ));
        }
        return rows;
    }

    @Transactional
    public int addStudyMinute(String userId, String lessonId) {
        ensureLearningInitialized(userId);
        UserLessonEntity lesson = requireLesson(userId, lessonId);
        UserLearningProfileEntity profile = requireProfile(userId);
        lesson.setStudyMinutes(lesson.getStudyMinutes() + 1);
        lessonRepository.save(lesson);
        profile.setStudyMinutes(profile.getStudyMinutes() + 1);
        profileRepository.save(profile);
        return profile.getStudyMinutes();
    }

    @Transactional
    public Map<String, Object> metricsMe(String userId) {
        ensureLearningInitialized(userId);
        UserLearningProfileEntity profile = requireProfile(userId);
        return Maps.of(
                "completedLessons", (int) lessonRepository.countByIdUserIdAndCompletedTrue(userId),
                "totalLessons", (int) lessonRepository.countByIdUserId(userId),
                "studyMinutes", profile.getStudyMinutes(),
                "xp", profile.getTotalXp()
        );
    }

    @Transactional
    public Map<String, Object> xpMe(String userId) {
        ensureLearningInitialized(userId);
        UserLearningProfileEntity profile = requireProfile(userId);
        Map<String, Object> row = toXpMap(profile);
        row.put("userRanking", rankOf(userId));
        return row;
    }

    @Transactional
    public List<Map<String, Object>> xpUserRanking() {
        List<UserLearningProfileEntity> profiles = profileRepository.findAllByOrderByTotalXpDescUpdatedAtAsc();
        Map<String, UserEntity> usersById = userRepository.findAllById(profiles.stream().map(UserLearningProfileEntity::getUserId).toList()).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, user -> user));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            UserLearningProfileEntity profile = profiles.get(i);
            UserEntity user = usersById.get(profile.getUserId());
            rows.add(Maps.of(
                    "rank", i + 1,
                    "userId", profile.getUserId(),
                    "nickname", user == null ? "Unknown" : user.getNickname(),
                    "level", profile.getLevel(),
                    "totalXp", profile.getTotalXp()
            ));
        }
        return rows;
    }

    @Transactional
    public List<Map<String, Object>> listBadges(String userId) {
        ensureLearningInitialized(userId);
        int completedLessons = (int) lessonRepository.countByIdUserIdAndCompletedTrue(userId);
        long favoriteCount = favoriteStockRepository.countByIdUserId(userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> badge : appState.listBadges()) {
            Map<String, Object> row = new LinkedHashMap<>(badge);
            String id = Maps.str(row, "id");
            boolean earned = switch (id) {
                case "2" -> completedLessons >= 5;
                case "7" -> favoriteCount >= 10;
                default -> false;
            };
            row.put("earned", earned);
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> memberGamificationSummary(String userId) {
        ensureLearningInitialized(userId);
        UserLearningProfileEntity profile = requireProfile(userId);
        List<Map<String, Object>> earnedBadges = new ArrayList<>();
        for (Map<String, Object> badge : listBadges(userId)) {
            if (Boolean.TRUE.equals(badge.get("earned"))) {
                earnedBadges.add(badge);
            }
        }
        Integer ranking = null;
        List<Map<String, Object>> rankings = xpUserRanking();
        for (Map<String, Object> row : rankings) {
            if (userId.equals(Maps.str(row, "userId"))) {
                ranking = Maps.intVal(row, "rank");
                break;
            }
        }
        return Maps.of(
                "userId", userId,
                "badges", earnedBadges,
                "ranking", ranking,
                "totalXp", profile.getTotalXp(),
                "level", profile.getLevel()
        );
    }

    @Transactional
    public Map<String, Object> squadContributionMe(String userId) {
        ensureLearningInitialized(userId);
        Optional<UserSquadMembershipEntity> membership = squadMembershipRepository.findById(userId);
        if (membership.isEmpty()) {
            return Maps.of(
                    "joined", false,
                    "squadId", null,
                    "squadName", null,
                    "items", toContributionRows(profitSnapshotDailyService.rankUsersByStockReturn(List.of()))
            );
        }
        Map<String, Object> squad = requireSquadDefinition(membership.get().getSquadId());
        List<String> memberUserIds = squadMembershipRepository.findAllBySquadId(membership.get().getSquadId())
                .stream()
                .map(UserSquadMembershipEntity::getUserId)
                .toList();
        return Maps.of(
                "joined", true,
                "squadId", membership.get().getSquadId(),
                "squadName", Maps.str(squad, "name"),
                "items", toContributionRows(profitSnapshotDailyService.rankUsersByStockReturn(memberUserIds))
        );
    }

    @Transactional
    public List<Map<String, Object>> listChallenges(String userId) {
        ensureLearningInitialized(userId);
        syncDerivedChallengeProgress(userId);
        Map<String, UserChallengeProgressEntity> progressByChallengeId = challengeProgressRepository
                .findAllByIdUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .collect(Collectors.toMap(UserChallengeProgressEntity::getChallengeId, item -> item, (first, second) -> first));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> challenge : appState.listChallenges()) {
            UserChallengeProgressEntity progress = progressByChallengeId.get(Maps.str(challenge, "id"));
            rows.add(toChallengeMap(challenge, progress));
        }
        return rows;
    }

    @Transactional
    public List<Map<String, Object>> completedChallenges(String userId) {
        ensureLearningInitialized(userId);
        syncDerivedChallengeProgress(userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserChallengeProgressEntity progress : challengeProgressRepository.findAllByIdUserIdAndStatusOrderByUpdatedAtDesc(userId, "completed")) {
            rows.add(toChallengeMap(findChallengeDefinition(progress.getChallengeId()).orElse(Maps.of(
                    "id", progress.getChallengeId(),
                    "title", progress.getChallengeId(),
                    "description", "완료한 챌린지",
                    "participants", 0,
                    "timeLeft", "완료",
                    "category", "custom"
            )), progress));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> completeChallenge(String userId, String challengeId) {
        ensureLearningInitialized(userId);
        Map<String, Object> challenge = requireChallengeDefinition(challengeId);
        UserChallengeProgressEntity progress = challengeProgressRepository.findByIdUserIdAndIdChallengeId(userId, challengeId)
                .orElseGet(() -> {
                    UserChallengeProgressEntity created = new UserChallengeProgressEntity();
                    created.setId(new UserChallengeProgressId(userId, challengeId));
                    return created;
                });
        progress.setStatus("completed");
        if (progress.getCompletedAt() == null) {
            progress.setCompletedAt(LocalDateTime.now(TimeUtil.SEOUL));
        }
        challengeProgressRepository.save(progress);
        return toChallengeMap(challenge, progress);
    }

    @Transactional
    public List<Map<String, Object>> listSquads(String userId) {
        ensureLearningInitialized(userId);
        String joinedSquadId = squadMembershipRepository.findById(userId)
                .map(UserSquadMembershipEntity::getSquadId)
                .orElse(null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> squad : appState.listSquads()) {
            rows.add(toSquadMap(squad, joinedSquadId));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> squadMe(String userId) {
        ensureLearningInitialized(userId);
        Optional<UserSquadMembershipEntity> membership = squadMembershipRepository.findById(userId);
        if (membership.isEmpty()) {
            return Maps.of("joined", false, "squad", null);
        }
        return toSquadMap(requireSquadDefinition(membership.get().getSquadId()), membership.get().getSquadId());
    }

    @Transactional
    public Map<String, Object> joinSquad(String userId, String squadId) {
        ensureLearningInitialized(userId);
        Map<String, Object> squad = requireSquadDefinition(squadId);
        UserSquadMembershipEntity membership = squadMembershipRepository.findById(userId)
                .orElseGet(() -> {
                    UserSquadMembershipEntity created = new UserSquadMembershipEntity();
                    created.setUserId(userId);
                    return created;
                });
        membership.setSquadId(squadId);
        squadMembershipRepository.save(membership);
        if (findChallengeDefinition("challenge-squad").isPresent()) {
            completeChallenge(userId, "challenge-squad");
        }
        return toSquadMap(squad, squadId);
    }

    @Transactional
    public List<Map<String, Object>> xpSquadsRanking() {
        Map<String, Map<String, Object>> definitionsById = squadDefinitionsById();
        Map<String, UserSquadMembershipEntity> membershipByUserId = squadMembershipRepository.findAll()
                .stream()
                .collect(Collectors.toMap(UserSquadMembershipEntity::getUserId, membership -> membership, (first, second) -> first));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> ranking : profitSnapshotDailyService.rankUsersByStockReturn(List.of())) {
            String rankedUserId = Maps.str(ranking, "userId");
            UserSquadMembershipEntity membership = membershipByUserId.get(rankedUserId);
            String joinedSquadId = membership == null ? null : membership.getSquadId();
            Map<String, Object> squad = joinedSquadId == null ? null : definitionsById.get(joinedSquadId);
            double returnRate = Maps.doubleVal(ranking, "totalReturnRate");

            Map<String, Object> row = new LinkedHashMap<>(ranking);
            row.put("squadId", rankedUserId);
            row.put("squadName", Maps.str(ranking, "nickname", "Unknown"));
            row.put("joinedSquadId", joinedSquadId);
            row.put("joinedSquadName", squad == null ? null : Maps.str(squad, "name"));
            row.put("groupReturnRate", returnRate);
            row.put("weeklyXpChangeRate", returnRate);
            row.put("rankingChange", 0);
            row.put("scoreType", "stockReturnRate");
            row.put("score", returnRate);
            row.put("xp", Math.round(returnRate * 100.0) / 100.0);
            row.put("weeklyXp", Math.round(returnRate * 100.0) / 100.0);
            row.put("totalXp", Math.round(returnRate * 100.0) / 100.0);
            row.put("members", joinedSquadId == null ? 0 : squadMembershipRepository.countBySquadId(joinedSquadId));
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> weeklyGoalFor(String userId) {
        ensureLearningInitialized(userId);
        Map<String, Object> legacyDashboard = appState.learningDashboard();
        Map<String, Object> goal = new LinkedHashMap<>(Maps.map(legacyDashboard.get("weeklyGoal")));
        Map<String, Object> metrics = metricsMe(userId);
        goal.put("courseCompletionCurrent", metrics.get("completedLessons"));
        goal.put("studyHoursCurrent", Math.round((Maps.intVal(metrics, "studyMinutes") / 60.0) * 10.0) / 10.0);
        return goal;
    }

    private void syncDerivedChallengeProgress(String userId) {
        int completedLessons = (int) lessonRepository.countByIdUserIdAndCompletedTrue(userId);
        long favoriteCount = favoriteStockRepository.countByIdUserId(userId);
        boolean joinedSquad = squadMembershipRepository.existsById(userId);
        if (completedLessons >= 1) {
            markChallengeCompletedIfAbsent(userId, "challenge-first-lesson");
        }
        if (completedLessons >= 5) {
            markChallengeCompletedIfAbsent(userId, "challenge-learning-5");
        }
        if (favoriteCount >= 3) {
            markChallengeCompletedIfAbsent(userId, "challenge-watchlist-3");
        }
        if (joinedSquad) {
            markChallengeCompletedIfAbsent(userId, "challenge-squad");
        }
    }

    private void markChallengeCompletedIfAbsent(String userId, String challengeId) {
        UserChallengeProgressEntity progress = challengeProgressRepository.findByIdUserIdAndIdChallengeId(userId, challengeId)
                .orElseGet(() -> {
                    UserChallengeProgressEntity created = new UserChallengeProgressEntity();
                    created.setId(new UserChallengeProgressId(userId, challengeId));
                    return created;
                });
        if (!"completed".equals(progress.getStatus())) {
            progress.setStatus("completed");
            progress.setCompletedAt(LocalDateTime.now(TimeUtil.SEOUL));
            challengeProgressRepository.save(progress);
        }
    }

    private Map<String, Map<String, Object>> derivedChallengeDefinitions() {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        rows.put("challenge-first-lesson", Maps.of(
                "id", "challenge-first-lesson",
                "title", "첫 학습 완료",
                "description", "첫 번째 투자 학습 레슨을 완료했습니다.",
                "participants", 0,
                "timeLeft", "완료",
                "category", "learning"
        ));
        rows.put("challenge-learning-5", Maps.of(
                "id", "challenge-learning-5",
                "title", "지식 탐구 챌린지",
                "description", "AI 학습 콘텐츠를 5개 이상 완료했습니다.",
                "participants", 0,
                "timeLeft", "완료",
                "category", "learning"
        ));
        rows.put("challenge-watchlist-3", Maps.of(
                "id", "challenge-watchlist-3",
                "title", "관심종목 탐색",
                "description", "관심종목을 3개 이상 등록했습니다.",
                "participants", 0,
                "timeLeft", "완료",
                "category", "market"
        ));
        rows.put("challenge-squad", Maps.of(
                "id", "challenge-squad",
                "title", "스쿼드 참가",
                "description", "투자 스쿼드에 참가했습니다.",
                "participants", 0,
                "timeLeft", "완료",
                "category", "squad"
        ));
        return rows;
    }

    private Map<String, Object> toChallengeMap(Map<String, Object> challenge, UserChallengeProgressEntity progress) {
        Map<String, Object> row = new LinkedHashMap<>(challenge);
        String status = progress == null ? Maps.str(row, "status", "active") : progress.getStatus();
        row.put("status", status);
        row.put("completed", "completed".equals(status));
        if (progress != null && progress.getCompletedAt() != null) {
            row.put("completedAt", progress.getCompletedAt().atOffset(ZoneOffset.ofHours(9)).toString());
        }
        return row;
    }

    private Optional<Map<String, Object>> findChallengeDefinition(String challengeId) {
        for (Map<String, Object> challenge : appState.listChallenges()) {
            if (challengeId.equals(Maps.str(challenge, "id"))) {
                return Optional.of(challenge);
            }
        }
        for (Map<String, Object> challenge : appState.listCompletedChallenges()) {
            if (challengeId.equals(Maps.str(challenge, "id"))) {
                return Optional.of(challenge);
            }
        }
        Map<String, Object> derived = derivedChallengeDefinitions().get(challengeId);
        return derived == null ? Optional.empty() : Optional.of(derived);
    }

    private Map<String, Object> requireChallengeDefinition(String challengeId) {
        return findChallengeDefinition(challengeId)
                .orElseThrow(() -> ApiException.notFound("CHALLENGE_NOT_FOUND", "챌린지를 찾을 수 없습니다: " + challengeId));
    }

    private Map<String, Object> toSquadMap(Map<String, Object> squad, String joinedSquadId) {
        Map<String, Object> row = new LinkedHashMap<>(squad);
        String squadId = Maps.str(row, "id");
        row.put("members", Maps.intVal(row, "members") + squadMembershipRepository.countBySquadId(squadId));
        row.put("joined", squadId.equals(joinedSquadId));
        return row;
    }

    private List<Map<String, Object>> toContributionRows(List<Map<String, Object>> returnRankings) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> ranking : returnRankings) {
            double returnRate = Maps.doubleVal(ranking, "totalReturnRate");
            Map<String, Object> row = new LinkedHashMap<>(ranking);
            row.put("nickname", Maps.str(ranking, "nickname", "Unknown"));
            row.put("ranking", Maps.intVal(ranking, "rank"));
            row.put("weeklyContributionXp", Math.round(returnRate * 100.0) / 100.0);
            row.put("returnRate", returnRate);
            row.put("stockReturnRate", returnRate);
            row.put("scoreType", "stockReturnRate");
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> requireSquadDefinition(String squadId) {
        Map<String, Object> squad = squadDefinitionsById().get(squadId);
        if (squad == null) {
            throw ApiException.notFound("SQUAD_NOT_FOUND", "스쿼드를 찾을 수 없습니다: " + squadId);
        }
        return squad;
    }

    private Map<String, Map<String, Object>> squadDefinitionsById() {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (Map<String, Object> squad : appState.listSquads()) {
            rows.put(Maps.str(squad, "id"), squad);
        }
        return rows;
    }

    private void ensureLearningInitialized(String userId) {
        Optional<UserLearningProfileEntity> profile = profileRepository.findById(userId);
        if (profile.isEmpty()) {
            UserLearningProfileEntity entity = new UserLearningProfileEntity();
            entity.setUserId(userId);
            entity.setLevel(1);
            entity.setTitle("입문 투자자");
            entity.setTotalXp(0);
            entity.setXpToNextLevel(XP_PER_LEVEL);
            entity.setStudyMinutes(0);
            entity.setUserRanking(0);
            entity.setSquadRanking(0);
            profileRepository.save(entity);
        }
        if (courseRepository.countByIdUserId(userId) == 0) {
            seedBaseCourses(userId);
        }
    }

    private void seedBaseCourses(String userId) {
        List<Map<String, Object>> courseSeeds = toMapList(FinvibeUtils.loadJsonResource("courses.json"));
        for (int courseIndex = 0; courseIndex < courseSeeds.size(); courseIndex++) {
            Map<String, Object> seed = courseSeeds.get(courseIndex);
            String courseId = Maps.str(seed, "id");
            List<String> lessonTitles = toStringList(seed.get("lessons"));

            UserCourseEntity course = new UserCourseEntity();
            course.setId(new UserCourseId(userId, courseId));
            course.setTitle(Maps.str(seed, "title"));
            course.setDescription(Maps.str(seed, "description", ""));
            course.setLevel(Maps.str(seed, "level", "beginner"));
            course.setKeywordsJson(Json.stringify(List.of()));
            course.setSortOrder(courseIndex + 1);
            courseRepository.save(course);

            for (int lessonIndex = 0; lessonIndex < lessonTitles.size(); lessonIndex++) {
                UserLessonEntity lesson = new UserLessonEntity();
                lesson.setId(new UserLessonId(userId, courseId + "-lesson-" + (lessonIndex + 1)));
                lesson.setCourseId(courseId);
                lesson.setTitle(lessonTitles.get(lessonIndex));
                lesson.setDurationMinutes(5 + (((lessonIndex + 1) * 3) % 7));
                lesson.setCompleted(false);
                lesson.setCompletedAt(null);
                lesson.setStudyMinutes(0);
                lesson.setSortOrder(lessonIndex + 1);
                lessonRepository.save(lesson);
            }
        }
    }

    private int nextCourseSortOrder(String userId) {
        return courseRepository.findAllByIdUserIdOrderBySortOrderAsc(userId).stream()
                .map(UserCourseEntity::getSortOrder)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    private int rankOf(String userId) {
        List<UserLearningProfileEntity> profiles = profileRepository.findAllByOrderByTotalXpDescUpdatedAtAsc();
        for (int i = 0; i < profiles.size(); i++) {
            if (userId.equals(profiles.get(i).getUserId())) {
                return i + 1;
            }
        }
        return 0;
    }

    private UserLearningProfileEntity requireProfile(String userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("LEARNING_PROFILE_NOT_FOUND", "학습 프로필을 찾을 수 없습니다."));
    }

    private UserCourseEntity requireCourse(String userId, String courseId) {
        return courseRepository.findByIdUserIdAndIdCourseId(userId, courseId)
                .orElseThrow(() -> ApiException.notFound("COURSE_NOT_FOUND", "코스를 찾을 수 없습니다: " + courseId));
    }

    private UserLessonEntity requireLesson(String userId, String lessonId) {
        return lessonRepository.findByIdUserIdAndIdLessonId(userId, lessonId)
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_FOUND", "레슨을 찾을 수 없습니다: " + lessonId));
    }

    private Map<String, Object> toCourseMap(UserCourseEntity course, List<UserLessonEntity> lessons) {
        List<Map<String, Object>> lessonDetails = new ArrayList<>();
        List<String> lessonTitles = new ArrayList<>();
        int completedCount = 0;
        for (UserLessonEntity lesson : lessons) {
            lessonDetails.add(toLessonMap(course, lesson, false));
            lessonTitles.add(lesson.getTitle());
            if (lesson.isCompleted()) {
                completedCount++;
            }
        }
        int progress = lessons.isEmpty() ? 0 : (int) Math.round(completedCount * 100.0 / lessons.size());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", course.getId().getCourseId());
        row.put("title", course.getTitle());
        row.put("description", course.getDescription());
        row.put("level", course.getLevel());
        row.put("progress", progress);
        row.put("completed", !lessons.isEmpty() && completedCount == lessons.size());
        row.put("keywords", parseStringListJson(course.getKeywordsJson()));
        row.put("lessons", lessonTitles);
        row.put("lessonDetails", lessonDetails);
        return row;
    }

    private Map<String, Object> toLessonMap(UserCourseEntity course, UserLessonEntity lesson, boolean includeCourseDetails) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", lesson.getId().getLessonId());
        row.put("title", lesson.getTitle());
        row.put("durationMinutes", lesson.getDurationMinutes());
        row.put("completed", lesson.isCompleted());
        row.put("studyMinutes", lesson.getStudyMinutes());
        if (lesson.getCompletedAt() != null) {
            row.put("completedAt", lesson.getCompletedAt().atOffset(ZoneOffset.ofHours(9)).toString());
        }
        if (includeCourseDetails) {
            row.put("courseId", course.getId().getCourseId());
            row.put("courseTitle", course.getTitle());
            row.put("description", lesson.getTitle() + " 학습을 통해 실전 투자에 바로 활용할 수 있는 핵심 개념을 익힙니다.");
        }
        return row;
    }

    private Map<String, Object> toXpMap(UserLearningProfileEntity profile) {
        return Maps.of(
                "level", profile.getLevel(),
                "title", profile.getTitle(),
                "totalXp", profile.getTotalXp(),
                "xpToNextLevel", profile.getXpToNextLevel(),
                "userRanking", profile.getUserRanking(),
                "squadRanking", profile.getSquadRanking()
        );
    }

    private void applyXpLevel(UserLearningProfileEntity profile) {
        int totalXp = Math.max(profile.getTotalXp(), 0);
        int level = Math.max(1, (totalXp / XP_PER_LEVEL) + 1);
        int nextThreshold = level * XP_PER_LEVEL;
        profile.setLevel(level);
        profile.setXpToNextLevel(Math.max(nextThreshold - totalXp, 0));
        profile.setTitle(level >= 10 ? "고급 투자자" : level >= 5 ? "중급 투자자" : "입문 투자자");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add((Map<String, Object>) map);
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private List<String> parseStringListJson(String json) {
        try {
            return toStringList(Json.parse(json == null || json.isBlank() ? "[]" : json));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }
}
