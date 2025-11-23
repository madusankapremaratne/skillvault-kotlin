package com.knovik.skillvault.data.importer

import com.google.gson.Gson
import com.knovik.skillvault.data.entity.Resume
import timber.log.Timber
import java.security.MessageDigest

/**
 * Maps CSV columns to Resume entity fields.
 * Supports flexible column mapping for different CSV formats.
 */
object FlexibleCSVMapper {

    private val gson = Gson()

    /**
     * Map CSV row to Resume entity based on detected format.
     */
    fun mapToResume(
        values: Map<String, String>,
        formatType: CSVFormatDetector.FormatType,
        sourceFile: String
    ): Resume {
        return when (formatType) {
            CSVFormatDetector.FormatType.KAGGLE_RESUME_DATASET -> mapKaggleFormat(values, sourceFile)
            CSVFormatDetector.FormatType.EXTENDED_RESUME_DATASET -> mapExtendedFormat(values, sourceFile)
            CSVFormatDetector.FormatType.CUSTOM -> mapCustomFormat(values, sourceFile)
        }
    }

    /**
     * Map Kaggle Resume Dataset format.
     */
    private fun mapKaggleFormat(values: Map<String, String>, sourceFile: String): Resume {
        val rawText = values.values.filter { it.isNotBlank() }.joinToString(" ")
        val textHash = sha256(rawText)

        return Resume(
            resumeId = values.getIgnoreCase("id") ?: "kaggle_${System.currentTimeMillis()}",
            fullName = values.getIgnoreCase("name") ?: "",
            email = values.getIgnoreCase("email") ?: "",
            phoneNumber = values.getIgnoreCase("phone") ?: "",
            summary = values.getIgnoreCase("summary") ?: "",
            skills = values.getIgnoreCase("skills") ?: "",
            experience = values.getIgnoreCase("experience") ?: "",
            education = values.getIgnoreCase("education") ?: "",
            certifications = values.getIgnoreCase("certifications") ?: "",
            rawText = rawText,
            textHash = textHash,
            fileFormat = "csv",
            sourceFile = sourceFile,
            processingStatus = "pending"
        )
    }

    /**
     * Map Extended Resume Dataset format (35 columns).
     */
    private fun mapExtendedFormat(values: Map<String, String>, sourceFile: String): Resume {
        // Extract and combine skills from multiple columns
        val skills = combineSkills(
            values.getIgnoreCase("skills"),
            values.getIgnoreCase("related_skils_in_job"),
            values.getIgnoreCase("certification_skills"),
            values.getIgnoreCase("skills_required")
        )

        // Build education section from multiple columns
        val education = buildEducationSection(
            institutionName = values.getIgnoreCase("educational_institution_name"),
            degreeNames = values.getIgnoreCase("degree_names"),
            passingYears = values.getIgnoreCase("passing_years"),
            results = values.getIgnoreCase("educational_results"),
            resultTypes = values.getIgnoreCase("result_types"),
            majorFields = values.getIgnoreCase("major_field_of_studies")
        )

        // Build experience section from multiple columns
        val experience = buildExperienceSection(
            companyNames = values.getIgnoreCase("professional_company_names"),
            companyUrls = values.getIgnoreCase("company_urls"),
            startDates = values.getIgnoreCase("start_dates"),
            endDates = values.getIgnoreCase("end_dates"),
            positions = values.getIgnoreCase("positions"),
            locations = values.getIgnoreCase("locations"),
            responsibilities = values.getIgnoreCase("responsibilities")
        )

        // Build certifications section
        val certifications = buildCertificationsSection(
            providers = values.getIgnoreCase("certification_providers"),
            skills = values.getIgnoreCase("certification_skills"),
            links = values.getIgnoreCase("online_links"),
            issueDates = values.getIgnoreCase("issue_dates"),
            expiryDates = values.getIgnoreCase("expiry_dates")
        )

        // Build summary from career objective
        val summary = values.getIgnoreCase("career_objective") ?: ""

        // Additional fields stored in rawText for embedding
        val additionalInfo = buildAdditionalInfo(
            address = values.getIgnoreCase("address"),
            languages = values.getIgnoreCase("languages"),
            proficiency = values.getIgnoreCase("proficiency_levels"),
            extraCurricular = buildExtraCurricularSection(
                types = values.getIgnoreCase("extra_curricular_activity_types"),
                organizations = values.getIgnoreCase("extra_curricular_organization_names"),
                links = values.getIgnoreCase("extra_curricular_organization_links"),
                roles = values.getIgnoreCase("role_positions")
            )
        )

        // Combine all text for rawText
        val rawText = listOf(
            summary, skills, education, experience, certifications, additionalInfo
        ).filter { it.isNotBlank() }.joinToString("\n\n")

        val textHash = sha256(rawText)

        return Resume(
            resumeId = "extended_${System.currentTimeMillis()}_${textHash.take(8)}",
            fullName = "", // Not in this dataset
            email = "",
            phoneNumber = "",
            summary = summary,
            skills = skills,
            experience = experience,
            education = education,
            certifications = certifications,
            rawText = rawText,
            textHash = textHash,
            fileFormat = "csv",
            sourceFile = sourceFile,
            processingStatus = "pending"
        )
    }

    /**
     * Map custom format - best effort mapping.
     */
    private fun mapCustomFormat(values: Map<String, String>, sourceFile: String): Resume {
        val rawText = values.values.filter { it.isNotBlank() }.joinToString(" ")
        val textHash = sha256(rawText)

        // Try to find common fields
        val name = values.getIgnoreCase("name") ?: values.getIgnoreCase("fullname") ?: ""
        val email = values.getIgnoreCase("email") ?: ""
        val phone = values.getIgnoreCase("phone") ?: values.getIgnoreCase("phonenumber") ?: ""
        val skills = values.getIgnoreCase("skills") ?: ""
        val summary = values.getIgnoreCase("summary") ?: values.getIgnoreCase("objective") ?: ""

        return Resume(
            resumeId = "custom_${System.currentTimeMillis()}",
            fullName = name,
            email = email,
            phoneNumber = phone,
            summary = summary,
            skills = skills,
            experience = "",
            education = "",
            certifications = "",
            rawText = rawText,
            textHash = textHash,
            fileFormat = "csv",
            sourceFile = sourceFile,
            processingStatus = "pending"
        )
    }

    // Helper functions

    private fun combineSkills(vararg skillSets: String?): String {
        val allSkills = skillSets
            .filterNotNull()
            .flatMap { it.split(",", ";", "|") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        
        return allSkills.joinToString(", ")
    }

    private fun buildEducationSection(
        institutionName: String?,
        degreeNames: String?,
        passingYears: String?,
        results: String?,
        resultTypes: String?,
        majorFields: String?
    ): String {
        val parts = mutableListOf<String>()

        if (!degreeNames.isNullOrBlank()) {
            parts.add("Degree: $degreeNames")
        }
        if (!institutionName.isNullOrBlank()) {
            parts.add("Institution: $institutionName")
        }
        if (!majorFields.isNullOrBlank()) {
            parts.add("Major: $majorFields")
        }
        if (!passingYears.isNullOrBlank()) {
            parts.add("Year: $passingYears")
        }
        if (!results.isNullOrBlank()) {
            parts.add("Result: $results")
        }

        return parts.joinToString(" | ")
    }

    private fun buildExperienceSection(
        companyNames: String?,
        companyUrls: String?,
        startDates: String?,
        endDates: String?,
        positions: String?,
        locations: String?,
        responsibilities: String?
    ): String {
        val parts = mutableListOf<String>()

        if (!positions.isNullOrBlank()) {
            parts.add("Position: $positions")
        }
        if (!companyNames.isNullOrBlank()) {
            parts.add("Company: $companyNames")
        }
        if (!locations.isNullOrBlank()) {
            parts.add("Location: $locations")
        }
        if (!startDates.isNullOrBlank() || !endDates.isNullOrBlank()) {
            val period = "${startDates ?: "N/A"} - ${endDates ?: "Present"}"
            parts.add("Period: $period")
        }
        if (!responsibilities.isNullOrBlank()) {
            parts.add("Responsibilities: $responsibilities")
        }
        if (!companyUrls.isNullOrBlank()) {
            parts.add("URL: $companyUrls")
        }

        return parts.joinToString(" | ")
    }

    private fun buildCertificationsSection(
        providers: String?,
        skills: String?,
        links: String?,
        issueDates: String?,
        expiryDates: String?
    ): String {
        val parts = mutableListOf<String>()

        if (!providers.isNullOrBlank()) {
            parts.add("Provider: $providers")
        }
        if (!skills.isNullOrBlank()) {
            parts.add("Skills: $skills")
        }
        if (!issueDates.isNullOrBlank()) {
            parts.add("Issued: $issueDates")
        }
        if (!expiryDates.isNullOrBlank()) {
            parts.add("Expires: $expiryDates")
        }
        if (!links.isNullOrBlank()) {
            parts.add("Link: $links")
        }

        return parts.joinToString(" | ")
    }

    private fun buildExtraCurricularSection(
        types: String?,
        organizations: String?,
        links: String?,
        roles: String?
    ): String {
        val parts = mutableListOf<String>()

        if (!types.isNullOrBlank()) {
            parts.add("Activity: $types")
        }
        if (!organizations.isNullOrBlank()) {
            parts.add("Organization: $organizations")
        }
        if (!roles.isNullOrBlank()) {
            parts.add("Role: $roles")
        }
        if (!links.isNullOrBlank()) {
            parts.add("Link: $links")
        }

        return parts.joinToString(" | ")
    }

    private fun buildAdditionalInfo(
        address: String?,
        languages: String?,
        proficiency: String?,
        extraCurricular: String?
    ): String {
        val parts = mutableListOf<String>()

        if (!address.isNullOrBlank()) {
            parts.add("Address: $address")
        }
        if (!languages.isNullOrBlank() || !proficiency.isNullOrBlank()) {
            val langInfo = if (!languages.isNullOrBlank() && !proficiency.isNullOrBlank()) {
                "Languages: $languages (Proficiency: $proficiency)"
            } else {
                "Languages: ${languages ?: proficiency}"
            }
            parts.add(langInfo)
        }
        if (!extraCurricular.isNullOrBlank()) {
            parts.add("Extra-curricular: $extraCurricular")
        }

        return parts.joinToString("\n")
    }

    /**
     * Case-insensitive map get.
     */
    private fun Map<String, String>.getIgnoreCase(key: String): String? {
        return this.entries.find { 
            it.key.equals(key, ignoreCase = true) 
        }?.value?.trim()
    }

    /**
     * Calculate SHA-256 hash.
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
