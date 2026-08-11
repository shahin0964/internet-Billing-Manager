package com.example.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SmsTemplateItem(
    val id: String,
    val title: String,
    val content: String,
    val isBuiltIn: Boolean = true
)

object SmsTemplateManager {
    private const val PREFS_NAME = "bulk_sms_template_prefs"
    private const val KEY_SMS_TEMPLATE = "sms_template_text"
    private const val KEY_CUSTOM_TEMPLATES = "custom_sms_templates_json"

    val BUILT_IN_TEMPLATES = listOf(
        SmsTemplateItem(
            id = "built_in_1",
            title = "১. মাসিক বিলের রিমাইন্ডার",
            content = "প্রিয় [Customer Name],\nআপনার মাসিক ইন্টারনেট বিল ৳[Monthly Fee]। অনুগ্রহ করে সময়মতো বিল পরিশোধ করে আপনার ইন্টারনেট সংযোগ সচল রাখুন।\nধন্যবাদ।\n[ISP Name]"
        ),
        SmsTemplateItem(
            id = "built_in_2",
            title = "২. বকেয়া বিলের বার্তা",
            content = "প্রিয় [Customer Name],\nআপনার ইন্টারনেট বিলের বকেয়া পরিমাণ ৳[Due Amount]। অনুগ্রহ করে দ্রুত বকেয়া বিল পরিশোধ করার জন্য অনুরোধ করা যাচ্ছে।\nধন্যবাদ।\n[ISP Name]"
        ),
        SmsTemplateItem(
            id = "built_in_3",
            title = "৩. বিল পরিশোধের অনুরোধ",
            content = "প্রিয় [Customer Name],\nআপনার [Package/Speed] ইন্টারনেট সংযোগের বিল পরিশোধের সময় হয়েছে। অনুগ্রহ করে নির্ধারিত সময়ের মধ্যে বিল পরিশোধ করুন।\nধন্যবাদ।\n[ISP Name]"
        ),
        SmsTemplateItem(
            id = "built_in_4",
            title = "৪. বিল পরিশোধের ধন্যবাদ",
            content = "প্রিয় [Customer Name],\nআপনার ইন্টারনেট বিল সফলভাবে পরিশোধ করার জন্য ধন্যবাদ। আপনার সহযোগিতা আমাদের জন্য অত্যন্ত গুরুত্বপূর্ণ।\nধন্যবাদ।\n[ISP Name]"
        ),
        SmsTemplateItem(
            id = "built_in_5",
            title = "৫. সংযোগ বন্ধ হওয়ার সতর্কবার্তা",
            content = "প্রিয় [Customer Name],\nআপনার ইন্টারনেট বিলের বকেয়া ৳[Due Amount]। নির্ধারিত সময়ের মধ্যে বিল পরিশোধ না করলে আপনার ইন্টারনেট সংযোগ সাময়িকভাবে বন্ধ হতে পারে।\nঅনুগ্রহ করে দ্রুত বিল পরিশোধ করুন।\n[ISP Name]"
        ),
        SmsTemplateItem(
            id = "built_in_6",
            title = "৬. নতুন মাসের বিল",
            content = "প্রিয় [Customer Name],\nনতুন মাসের জন্য আপনার [Package/Speed] ইন্টারনেট সংযোগের মাসিক বিল ৳[Monthly Fee] নির্ধারিত হয়েছে। অনুগ্রহ করে সময়মতো বিল পরিশোধ করুন।\nধন্যবাদ।\n[ISP Name]"
        ),
        SmsTemplateItem(
            id = "built_in_7",
            title = "৭. সাধারণ Customer Notice",
            content = "প্রিয় [Customer Name],\nআপনার ইন্টারনেট সংযোগ সংক্রান্ত গুরুত্বপূর্ণ তথ্য জানাতে এই বার্তাটি পাঠানো হয়েছে। প্রয়োজনে আমাদের সাথে যোগাযোগ করুন।\nধন্যবাদ।\n[ISP Name]"
        )
    )

    const val DEFAULT_TEMPLATE = "প্রিয় [Customer Name],\nআপনার মাসিক ইন্টারনেট বিল ৳[Monthly Fee]। অনুগ্রহ করে সময়মতো বিল পরিশোধ করে আপনার ইন্টারনেট সংযোগ সচল রাখুন।\nধন্যবাদ।\n[ISP Name]"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSmsTemplate(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_SMS_TEMPLATE, null) ?: DEFAULT_TEMPLATE
    }

    fun saveSmsTemplate(context: Context, template: String) {
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_SMS_TEMPLATE, template).apply()
    }

    fun getCustomTemplates(context: Context): List<SmsTemplateItem> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_CUSTOM_TEMPLATES, null) ?: return emptyList()
        val list = mutableListOf<SmsTemplateItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SmsTemplateItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        content = obj.getString("content"),
                        isBuiltIn = false
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveCustomTemplate(context: Context, title: String, content: String): SmsTemplateItem {
        return saveCustomTemplate(context, title, content, null)
    }

    fun saveCustomTemplate(context: Context, title: String, content: String, existingId: String?): SmsTemplateItem {
        val currentList = getCustomTemplates(context).toMutableList()
        val id = existingId ?: ("custom_" + System.currentTimeMillis())
        val newItem = SmsTemplateItem(
            id = id,
            title = title.ifBlank { "Custom Template" },
            content = content,
            isBuiltIn = false
        )
        if (existingId != null) {
            val index = currentList.indexOfFirst { it.id == existingId }
            if (index != -1) {
                currentList[index] = newItem
            } else {
                currentList.add(newItem)
            }
        } else {
            currentList.add(newItem)
        }
        saveCustomTemplatesList(context, currentList)
        return newItem
    }

    fun deleteCustomTemplate(context: Context, id: String) {
        val currentList = getCustomTemplates(context).filter { it.id != id }
        saveCustomTemplatesList(context, currentList)
    }

    private fun saveCustomTemplatesList(context: Context, list: List<SmsTemplateItem>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("content", item.content)
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_CUSTOM_TEMPLATES, jsonArray.toString()).apply()
    }

    fun getDynamicBillingMonth(isBn: Boolean): String {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val monthIdx = calendar.get(java.util.Calendar.MONTH)
        if (isBn) {
            val bnMonths = arrayOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
            val bnYear = year.toString()
                .replace("0", "০").replace("1", "১").replace("2", "২").replace("3", "৩").replace("4", "৪")
                .replace("5", "৫").replace("6", "৬").replace("7", "৭").replace("8", "৮").replace("9", "৯")
            return "${bnMonths[monthIdx]} $bnYear"
        } else {
            val enMonths = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
            return "${enMonths[monthIdx]} $year"
        }
    }

    fun replaceVariables(
        template: String,
        customerName: String = "",
        monthlyFee: String = "",
        dueAmount: String = "",
        packageName: String = "",
        phone: String = "",
        ispName: String = "",
        dueDate: String = "",
        paymentDate: String = "",
        customerId: String = ""
    ): String {
        val isBn = java.util.Locale.getDefault().language == "bn"
        val dynamicMonth = getDynamicBillingMonth(isBn)
        
        val cleanMonthlyFee = monthlyFee.replace("৳", "").trim()
        val cleanDueAmount = dueAmount.replace("৳", "").trim()

        return template
            // User requested format placeholders
            .replace("{customer_name}", customerName.ifBlank { "" })
            .replace("{billing_month}", dynamicMonth)
            .replace("{bill_month}", dynamicMonth)
            .replace("{monthly_bill}", cleanMonthlyFee.ifBlank { "0" })
            .replace("{bill_amount}", cleanMonthlyFee.ifBlank { "0" })
            .replace("{due_amount}", cleanDueAmount.ifBlank { "0" })
            .replace("{due_date}", dueDate.ifBlank { "" })
            .replace("{company_name}", ispName.ifBlank { "" })
            .replace("{payment_date}", paymentDate.ifBlank { "" })
            .replace("{customer_id}", customerId.ifBlank { "" })
            
            // Legacy / Old UI placeholders
            .replace("[Customer Name]", customerName)
            .replace("[গ্রাহকের নাম]", customerName)
            .replace("[Monthly Fee]", cleanMonthlyFee)
            .replace("[মাসিক বিল]", cleanMonthlyFee)
            .replace("[Due Amount]", cleanDueAmount)
            .replace("[বকেয়া পরিমাণ]", cleanDueAmount)
            .replace("[Package/Speed]", packageName)
            .replace("[প্যাকেজ]", packageName)
            .replace("[Phone Number]", phone)
            .replace("[ফোন নম্বর]", phone)
            .replace("[ISP Name]", ispName)
            .replace("[আইএসপি নাম]", ispName)
    }
}

