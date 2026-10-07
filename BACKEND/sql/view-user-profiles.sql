SELECT
    account.id AS account_id,
    account.email,
    profile.name,
    profile.education,
    profile.branch,
    profile.study_year,
    profile.experience,
    profile.target_role,
    profile.skills_by_job::jsonb AS skills_by_job
FROM accounts AS account
LEFT JOIN skill_profiles AS profile
    ON profile.account_id = account.id
ORDER BY account.id;
