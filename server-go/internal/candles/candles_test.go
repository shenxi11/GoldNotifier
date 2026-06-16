package candles

import (
	"testing"

	"goldnotifier/server-go/internal/model"
)

func TestAggregateBuildsOHLCByBucket(t *testing.T) {
	points := []model.GoldHistoryPoint{
		{TimestampMillis: 1_000, Price: 940.10},
		{TimestampMillis: 10_000, Price: 941.20},
		{TimestampMillis: 12_000, Price: 939.80},
		{TimestampMillis: 18_000, Price: 942.00},
	}

	bars := Aggregate(points, 0, 20_000, 15_000)

	if len(bars) != 2 {
		t.Fatalf("len(bars) = %d", len(bars))
	}
	first := bars[0]
	if first.TimestampMillis != 0 || first.Open != 940.10 || first.High != 941.20 || first.Low != 939.80 || first.Close != 939.80 {
		t.Fatalf("unexpected first bar: %+v", first)
	}
	second := bars[1]
	if second.TimestampMillis != 15_000 || second.Open != 942.00 || second.Close != 942.00 {
		t.Fatalf("unexpected second bar: %+v", second)
	}
}

func TestAggregateIgnoresInvalidAndOutOfWindowPoints(t *testing.T) {
	points := []model.GoldHistoryPoint{
		{TimestampMillis: -1, Price: 940.10},
		{TimestampMillis: 1_000, Price: -1},
		{TimestampMillis: 2_000, Price: 941.00},
		{TimestampMillis: 30_000, Price: 942.00},
	}

	bars := Aggregate(points, 0, 20_000, 15_000)

	if len(bars) != 1 {
		t.Fatalf("len(bars) = %d", len(bars))
	}
	if bars[0].Open != 941.00 {
		t.Fatalf("bars[0].Open = %f", bars[0].Open)
	}
}
