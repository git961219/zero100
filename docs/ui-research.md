# Zero100 UI/UX 트렌드 조사 보고서

> 조사일: 2026-04-18
> 조사자: 연구조사부
> 목적: Zero100 앱의 "전문적인 느낌" 확보를 위한 경쟁 앱 디자인 분석

---

## 1. 핵심 요약

**"전문적인 느낌"의 핵심 3요소:**
1. **다크 배경 + 고대비 강조색** -- 모든 선두 앱이 어두운 배경을 기본으로 사용
2. **대형 모노스페이스/타뷸러 숫자** -- 측정 중 핵심 수치(속도, 시간)를 극대화하여 표시
3. **정보 계층의 명확한 분리** -- 1차 정보(시간/속도)와 2차 정보(G-force, 온도 등)의 시각적 위계

---

## 2. 앱별 비교표

| 항목 | Dragy | RaceBox | VBOX Sport | RaceChrono Pro |
|---|---|---|---|---|
| **배경** | 다크(검정 계열) | 다크(확인 필요 - 다크모드 미지원 피드백 있음) | 기능적 레이아웃(밝은/어두운 혼합) | 다크 |
| **강조색** | 녹색/파란색 계열 | 컬러 팝업(섹터 타임 등 핵심 수치) | 전문 계측기 느낌(절제된 색상) | 다중 색상(텔레메트리 그래프) |
| **측정 대기 화면** | CONNECT 버튼 중심, 연결 상태 표시 | 모드 선택(Drag/Lap), GPS 상태 | Test/Results/Setup/Config 4탭 | 대시보드 커스터마이즈 가능 |
| **측정 중 화면** | 라이브 속도 대형 표시 + 원형 진행 인디케이터 | 큰 숫자, 설정값/예측 시간/실시간 속도/G-force/경사도/DA값/외기온 | 속도, 랩타임, 예측 랩타임 선택 표시 | 실시간 대시보드(랩타임, 델타, 텔레메트리) |
| **결과 화면** | 속도/고도/가속도 다중 그래프, 1/4마일 등 핵심 지표, 공유 카드 | 레이싱 라인 시각화(지도), 속도/G-force 그래프 | 가감속, VMAX 결과 테이블 | 동기화된 비디오+트랙맵+텔레메트리 그래프 |
| **폰트** | 디지털 계기 스타일 | 대형 산세리프(가독성 최우선) | 기능적 산세리프 | 커스터마이즈 가능한 게이지 |
| **정보 밀도** | 중간(핵심 수치 집중) | 높음(한 화면에 다수 데이터) | 낮음~중간(모드별 분리) | 높음(전문가 대상) |
| **특이 기능** | AI 타임카드 생성, 비디오 오버레이 | 예측 모드(실시간 랩 비교), 가로모드 | RACELOGIC 전문장비 연동 | 멀티카메라 동기화, 히트맵 |
| **대상 사용자** | 일반 차량 소유자~튜닝족 | 아마추어 레이서~준전문가 | 전문가/자동차 제조사 | 모터스포츠 매니아~전문가 |

### 출처
- Dragy: [godragy.com](https://www.godragy.com/), [Google Play](https://play.google.com/store/apps/details?id=com.dragy), [Apple App Store](https://apps.apple.com/us/app/dragy-connect/id1260905448)
- RaceBox: [racebox.pro/products/mobile-app](https://www.racebox.pro/products/mobile-app), [racebox.pro/features](https://www.racebox.pro/features)
- VBOX: [Apple App Store](https://apps.apple.com/us/app/vbox-sport-performance-test/id610418083), [Racelogic Support](https://en.racelogic.support/VBOX_Motorsport/Product_Info/Performance_Meters/VBOX_Sport/VBOX_Sport_(v3)/VBOX_Sport_(v3)_-_User_Guide/07_-_VBOX_Sport_Performance_Test_iOS_Application)
- RaceChrono: [racechrono.com](https://racechrono.com/), [Apple App Store](https://apps.apple.com/us/app/racechrono-pro/id1129429340)

---

## 3. 자동차 디지털 계기판 디자인 트렌드 (Tesla, BMW, Mercedes)

| 트렌드 | 설명 | Zero100 적용 가능성 |
|---|---|---|
| **다크 모드 기본** | 거의 모든 OEM이 다크 모드를 기본으로 채택. 라이트 모드는 선택사항 | 높음 -- 다크 모드 필수 |
| **미니멀 레이아웃** | 불필요한 요소 제거, 운전 중 인지 부하 최소화 | 높음 -- 측정 중 화면에 적용 |
| **컬러 악센트로 상태 표현** | 에코/스포츠 등 모드에 따라 악센트 컬러 변경 (예: 녹색=대기, 빨강=측정중) | 중간 |
| **대형 디스플레이 활용** | 12.3인치+ 클러스터, 정보를 크고 선명하게 | 높음 -- 핵심 수치 극대화 |
| **아날로그-디지털 하이브리드** | 원형 게이지 + 디지털 숫자 조합 | 중간 -- 속도계에 활용 가능 |
| **AI 개인화** | 사용자 행동 기반 UI 커스터마이즈 | 낮음(향후 과제) |
| **영화적 미학** | Blade Runner, Tron 등에서 영감받은 네온/글로우 효과 | 중간 -- 과하면 역효과 |

### 출처
- [ANODA: Future Car Dashboard UI Trends](https://www.anoda.mobi/ux-blog/future-car-dashboard-ui-trends-design-user-experience)
- [Dashboard UI Design in Cars 2025](https://govtcollegeofartanddesign.com/automotive-dashboard-ui-design-2025/)
- [Car Dashboard UX Trends 2025](https://thecompletedesignlab.co.uk/car-dashboard-ux-trends-2025/)
- [Dribbble: HMI Dashboard Automotive Dark theme](https://dribbble.com/shots/23507498-HMI-Dashboard-Automotive-Dark-theme)

---

## 4. "전문적인 느낌"을 내는 핵심 요소 정리

### 4.1 다크 모드 설계 원칙

| 원칙 | 상세 | 주의사항 |
|---|---|---|
| 순수 검정(#000000) 회피 | 짙은 회색(#121212 ~ #1E1E1E) 사용 | 순수 검정은 눈 피로 유발 |
| 텍스트 컬러 | 순백(#FFFFFF) 대신 약간 회색 빛(#E0E0E0 ~ #F0F0F0) | 과도한 대비 = 눈 피로 |
| 텍스트 불투명도 계층 | 고강조 87%, 중강조 60%, 비활성 38% (Google Material 기준) | 정보 위계 표현에 활용 |
| 악센트 컬러 | 다크 배경에서 시안/녹색/주황 등 고채도 컬러가 잘 돋보임 | 1~2가지로 제한 |

### 4.2 타이포그래피

| 요소 | 권장 | 이유 |
|---|---|---|
| 숫자 표시 | 모노스페이스 또는 타뷸러 숫자 폰트 | 숫자가 바뀔 때 레이아웃 흔들림 방지 |
| 본문 | 산세리프(SF Pro, Roboto, Inter 등) | 다크 배경에서 가독성 우수 |
| 크기 계층 | 핵심 수치 40pt+, 보조 정보 14~16pt, 라벨 12pt | 한눈에 위계 파악 |
| 자간/행간 | 다크 모드에서는 약간 넓게 | 가독성 향상 |

### 출처
- [Design Shack: Typography in Dark Mode](https://designshack.net/articles/typography/dark-mode-typography/)
- [Halo Lab: 11 Tips for Dark UI Design](https://www.halo-lab.com/blog/dark-ui-design-11-tips-for-dark-mode-design)
- [Fivejars: Mastering Dark Mode UI](https://fivejars.com/insights/dark-mode-ui-9-design-considerations-you-cant-ignore/)

---

## 5. Zero100에 적용할 구체적 디자인 포인트 10가지

### 즉시 적용 가능 (High Priority)

| # | 포인트 | 상세 설명 | 참고 앱 |
|---|---|---|---|
| 1 | **다크 배경 전면 적용** | 배경을 #121212~#1A1A1A로 통일. 순수 검정 회피. 카드/섹션은 #1E1E1E~#2A2A2A로 미세 구분 | 전체 트렌드 |
| 2 | **핵심 수치 극대화** | 측정 중 화면에서 0-100 시간을 화면의 40%+ 차지하도록 대형 표시 (48pt 이상). 나머지 정보는 작게 | RaceBox, Dragy |
| 3 | **모노스페이스/타뷸러 숫자 폰트** | 시간, 속도 등 변동 수치에 고정폭 숫자 폰트 사용 (예: JetBrains Mono, SF Mono, Roboto Mono) | 계기판 트렌드 |
| 4 | **강조색 1~2개로 통일** | 예: 시안(#00E5FF) 또는 녹색(#00E676)을 메인 악센트, 주황/빨강을 경고/기록갱신에 사용 | SpeedX, 자동차 HMI |
| 5 | **측정 상태별 시각 피드백** | 대기=차분한 색상, 측정중=악센트 컬러 펄스/글로우, 완료=결과 강조. 상태 전환을 색상으로 즉시 인지 | Dragy, RaceBox |

### 중기 적용 (Medium Priority)

| # | 포인트 | 상세 설명 | 참고 앱 |
|---|---|---|---|
| 6 | **결과 공유 카드 디자인** | 측정 결과를 SNS 공유용 이미지 카드로 자동 생성 (시간, 차량명, 날짜, 그래프 포함). Dragy의 "타임카드"가 대표 사례 | Dragy |
| 7 | **속도-시간 그래프 강화** | 결과 화면에 속도 vs 시간 그래프를 그라데이션 라인으로 표시. 가속 구간별 색상 구분 고려 | Dragy, RaceChrono |
| 8 | **정보 계층 3단계 분리** | 1단계: 핵심(시간/속도) = 대형+고대비, 2단계: 보조(G-force, 구간시간) = 중형+중대비, 3단계: 참고(날씨, GPS정밀도) = 소형+저대비 | RaceBox, VBOX |
| 9 | **빈 화면/대기 상태 처리** | "측정 기록이 없습니다" 대신 차량 실루엣 일러스트 + "첫 측정을 시작하세요" 같은 액션 유도 문구. 빈 화면도 브랜드 경험의 일부 | 전체 UI 트렌드 |

### 장기 적용 (Lower Priority)

| # | 포인트 | 상세 설명 | 참고 앱 |
|---|---|---|---|
| 10 | **가로 모드 지원** | 측정 중 가로 모드에서 더 큰 숫자와 넓은 그래프 영역 제공. 차량 거치 시 가로가 자연스러움 | RaceBox |

---

## 6. 색상 팔레트 제안

```
[배경 계층]
  Surface 0 (최하단):  #0D0D0D
  Surface 1 (카드):    #1A1A1A
  Surface 2 (상승):    #252525
  Surface 3 (모달):    #2E2E2E

[텍스트]
  Primary:             #E8E8E8  (87% white)
  Secondary:           #999999  (60% white)
  Disabled:            #616161  (38% white)

[악센트 - Option A: 시안 계열 (추천)]
  Primary Accent:      #00E5FF  (시안)
  Success/Record:      #00E676  (녹색)
  Warning:             #FFA726  (주황)
  Error:               #EF5350  (빨강)

[악센트 - Option B: 녹색 계열]
  Primary Accent:      #00E676  (녹색)
  Success/Record:      #00E5FF  (시안)
  Warning:             #FFA726  (주황)
  Error:               #EF5350  (빨강)
```

> 참고: 시안 계열(Option A)이 자동차 HMI 트렌드와 더 부합하며, "고급스러운" 인상을 줌.
> 녹색 계열(Option B)은 "스포티한" 인상이 강함.

---

## 7. 측정 화면 레이아웃 구조 제안

```
+------------------------------------------+
|  [GPS 상태]              [설정 아이콘]    |   <- 상단 바 (작게, 낮은 대비)
+------------------------------------------+
|                                          |
|                                          |
|              3.45 s                      |   <- 핵심 수치 (48pt+, 악센트 컬러)
|           0 - 100 km/h                   |   <- 라벨 (14pt, 중간 대비)
|                                          |
+------------------------------------------+
|   현재 속도        G-Force               |   <- 보조 정보 (20pt, 중간 대비)
|    67 km/h         0.43 G                |
+------------------------------------------+
|  [  =====>            ] 67%              |   <- 진행 바 (악센트 컬러)
+------------------------------------------+
|        [측정 중지 버튼]                   |   <- CTA 버튼
+------------------------------------------+
```

---

## 8. 확인 필요 사항

1. **RaceBox 다크 모드**: 사용자 리뷰에서 "다크 모드 미지원"이라는 피드백이 있음. 최신 버전에서 추가되었는지 확인 필요.
2. **Dragy 정확한 색상 코드**: 앱 스토어 스크린샷 기반 추정이며, 정확한 HEX 값은 실제 앱 설치 후 확인 필요.
3. **VBOX 앱 최신 UI**: VBOX 앱이 최근 UI 리뉴얼을 했다는 정보가 있으나 구체적 변경 내용은 확인 필요.
4. **폰트 라이센스**: JetBrains Mono(오픈소스), SF Mono(Apple 전용), Roboto Mono(Google 오픈소스) -- 플랫폼별 라이센스 확인 필요.

---

## 9. 조사 출처 종합

### 앱 공식 사이트
- [Dragy 공식](https://www.godragy.com/)
- [RaceBox 공식](https://www.racebox.pro/)
- [RaceBox Mobile App](https://www.racebox.pro/products/mobile-app)
- [RaceBox Features](https://www.racebox.pro/features)
- [VBOX Sport - Racelogic](https://www.vboxmotorsport.co.uk/index.php/en/vbox-sport)
- [RaceChrono 공식](https://racechrono.com/)

### 앱 스토어
- [Dragy - Google Play](https://play.google.com/store/apps/details?id=com.dragy)
- [Dragy Connect - Apple](https://apps.apple.com/us/app/dragy-connect/id1260905448)
- [RaceBox - Google Play](https://play.google.com/store/apps/details?id=pro.RaceBox.androidapp)
- [VBOX Sport - Apple](https://apps.apple.com/us/app/vbox-sport-performance-test/id610418083)
- [RaceChrono Pro - Apple](https://apps.apple.com/us/app/racechrono-pro/id1129429340)

### 리뷰/분석
- [Dragy Review - Jordi's Tire Shop](https://jordistireshop.com/dragy-review/)
- [Dragy Review - The Car Data](https://www.thecardata.com/aftermarket/dragy-review)
- [Dragy Review - LSX Mag](https://www.lsxmag.com/tech-stories/dragy-review-we-put-the-gps-performance-meter-to-the-test/)
- [RaceBox - Geeky Gadgets](https://www.geeky-gadgets.com/racebox-drag-meter-lap-timer-10-03-2020/)

### 디자인 트렌드/원칙
- [ANODA: Future Car Dashboard UI Trends](https://www.anoda.mobi/ux-blog/future-car-dashboard-ui-trends-design-user-experience)
- [Dashboard UI Design in Cars 2025](https://govtcollegeofartanddesign.com/automotive-dashboard-ui-design-2025/)
- [Car Dashboard UX Trends 2025](https://thecompletedesignlab.co.uk/car-dashboard-ux-trends-2025/)
- [UI/UX EV Dashboard Trends 2025](https://thecompletedesignlab.co.uk/ui-ux-ev-dashboard-trends-2025/)
- [SpeedX: Best-Looking Speedometer App](https://floatmaze.com/speedx/posts/Best-Looking-Speedometer-App-Clean-UI-Minimalist-Design-Dark-Mode.html)
- [Design Shack: Typography in Dark Mode](https://designshack.net/articles/typography/dark-mode-typography/)
- [Halo Lab: 11 Tips for Dark UI Design](https://www.halo-lab.com/blog/dark-ui-design-11-tips-for-dark-mode-design)
- [Fivejars: Mastering Dark Mode UI](https://fivejars.com/insights/dark-mode-ui-9-design-considerations-you-cant-ignore/)
- [WANDR: UX in Motorsports](https://www.wandr.studio/blog/the-role-of-ux-in-motorsports)
- [Dribbble: HMI Dashboard Automotive Dark theme](https://dribbble.com/shots/23507498-HMI-Dashboard-Automotive-Dark-theme)
