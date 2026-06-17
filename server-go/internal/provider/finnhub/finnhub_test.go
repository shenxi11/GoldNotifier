package finnhub

import (
	"testing"
	"time"
)

func TestBuildGoldPriceFromSnapshotConvertsToCNYPerGram(t *testing.T) {
	price, err := BuildGoldPriceFromSnapshot(
		QuoteSnapshot{
			XAUJPY: Quote{Current: 480000, Open: 470000, PrevClose: 475000, High: 481000, Low: 469000, LatestTimestampMS: 1_781_596_800_000},
			USDJPY: Quote{Current: 155, Open: 155, PrevClose: 155, High: 155, Low: 155, LatestTimestampMS: 1_781_596_800_000},
			USDCNY: Quote{Current: 7.2, Open: 7.2, PrevClose: 7.2, High: 7.2, Low: 7.2, LatestTimestampMS: 1_781_596_800_000},
		},
		"XAU",
		180,
		"Asia/Shanghai",
	)
	if err != nil {
		t.Fatal(err)
	}
	if price.Price != 716.86 {
		t.Fatalf("Price = %f", price.Price)
	}
	if price.Symbol != "XAU" || price.Unit != "元/克" || price.Source != "finnhub" {
		t.Fatalf("unexpected contract fields: %+v", price)
	}
}

func TestBuildGoldPriceFromSnapshotRejectsInvalidQuotes(t *testing.T) {
	_, err := BuildGoldPriceFromSnapshot(
		QuoteSnapshot{
			XAUJPY: Quote{Current: 0, Open: 1, PrevClose: 1, High: 1, Low: 1},
			USDJPY: Quote{Current: 155, Open: 155, PrevClose: 155, High: 155, Low: 155},
			USDCNY: Quote{Current: 7.2, Open: 7.2, PrevClose: 7.2, High: 7.2, Low: 7.2},
		},
		"XAU",
		180,
		"Asia/Shanghai",
	)
	if err == nil {
		t.Fatal("expected error")
	}
}

func TestStreamReconnectDelayCaps(t *testing.T) {
	cases := []struct {
		failures int
		want     time.Duration
	}{
		{failures: 0, want: 2 * time.Second},
		{failures: 1, want: 2 * time.Second},
		{failures: 3, want: 6 * time.Second},
		{failures: 99, want: 30 * time.Second},
	}
	for _, tc := range cases {
		if got := streamReconnectDelay(tc.failures); got != tc.want {
			t.Fatalf("streamReconnectDelay(%d) = %s, want %s", tc.failures, got, tc.want)
		}
	}
}
