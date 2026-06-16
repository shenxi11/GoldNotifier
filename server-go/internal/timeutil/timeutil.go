// 模块名: timeutil
// 功能概述: 提供服务端时间格式化、日期换算和行情时效判断。
// 对外接口: NowString、NowMillis、TimestampMillis、TodayDateString、IsTimeStale、MarketStatus
// 依赖关系: time
// 输入输出: 输入时区名或时间字符串，输出 Android 兼容时间字符串与 Unix 毫秒。
// 异常与错误: 时区或时间解析失败时回退 Asia/Shanghai 或返回 false/零值。
// 维护说明: 时间格式保持 yyyy-MM-dd HH:mm:ss，与 Python 服务端和 Android 展示一致。
package timeutil

import "time"

const Layout = "2006-01-02 15:04:05"

func NowString(timezone string) string {
	return time.Now().In(Location(timezone)).Format(Layout)
}

func NowMillis(timezone string) int64 {
	return time.Now().In(Location(timezone)).UnixMilli()
}

func TimestampMillis(value string, timezone string) (int64, bool) {
	for _, layout := range []string{Layout, "2006-01-02 15:04", "2006/01/02 15:04:05"} {
		parsed, err := time.ParseInLocation(layout, value, Location(timezone))
		if err == nil {
			return parsed.UnixMilli(), true
		}
	}
	return 0, false
}

func TodayDateString(timezone string) string {
	return time.Now().In(Location(timezone)).Format("2006-01-02")
}

func DateStringFromMillis(timestampMillis int64, timezone string) string {
	return time.UnixMilli(timestampMillis).In(Location(timezone)).Format("2006-01-02")
}

func PreviousDateString(value string) (string, bool) {
	parsed, err := time.Parse("2006-01-02", value)
	if err != nil {
		return "", false
	}
	return parsed.AddDate(0, 0, -1).Format("2006-01-02"), true
}

func IsDateString(value string) bool {
	_, err := time.Parse("2006-01-02", value)
	return err == nil
}

func IsTimeStale(updateTime string, staleAfterSeconds int, timezone string) bool {
	parsedMillis, ok := TimestampMillis(updateTime, timezone)
	if !ok {
		return true
	}
	return time.Since(time.UnixMilli(parsedMillis)) > time.Duration(staleAfterSeconds)*time.Second
}

func MarketStatus(timezone string) string {
	now := time.Now().In(Location(timezone))
	if now.Weekday() == time.Saturday || now.Weekday() == time.Sunday {
		return "closed"
	}
	current := now.Hour()*60 + now.Minute()
	dayStart := 9 * 60
	dayEnd := 15*60 + 30
	nightStart := 20 * 60
	nightEnd := 2*60 + 30
	if (current >= dayStart && current <= dayEnd) || current >= nightStart || current <= nightEnd {
		return "trading"
	}
	return "closed"
}

func Location(timezone string) *time.Location {
	location, err := time.LoadLocation(timezone)
	if err == nil {
		return location
	}
	location, err = time.LoadLocation("Asia/Shanghai")
	if err == nil {
		return location
	}
	return time.FixedZone("Asia/Shanghai", 8*60*60)
}
