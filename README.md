![image](https://user-images.githubusercontent.com/84304043/122708383-07f01000-d297-11eb-8352-20f9f9ce6b81.png)

# 창고대여(StorageRent)-2조

본 예제는 MSA/DDD/Event Storming/EDA 를 포괄하는 분석/설계/구현/운영 전단계를 커버하도록 구성한 예제입니다.
이는 클라우드 네이티브 애플리케이션의 개발에 요구되는 체크포인트들을 통과하기 위한 예시 답안을 포함합니다.
- 체크포인트 : https://workflowy.com/s/assessment-check-po/T5YrzcMewfo4J6LW


# Table of contents

- [예제 - 창고대여](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [체크포인트](#체크포인트)
  - [분석/설계](#분석설계)
  - [구현:](#구현-)
    - [DDD 의 적용](#ddd-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출-과-Eventual-Consistency)
  - [운영](#운영)
    - [CI/CD 설정](#cicd설정)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출-서킷-브레이킹-장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-재배포)
  - [신규 개발 조직의 추가](#신규-개발-조직의-추가)

# 서비스 시나리오

창고예약 커버하기

기능적 요구사항
1. 관리자가 창고정보를 등록/수정/삭제한다. 
2. 고객이 창고를 선택하여 예약한다.
3. 예약과 동시에 결제가 진행된다. 
4. 예약이 되면 예약 내역이 전달된다.
5. 고객이 예약을 취소할 수 있다. 
6. 예약사항이 취소될 경우 취소 내역이 전달된다.
7. 고객이 대여한 정보 및 예약상태 등을 한 화면에서 확인할 수 있다. (viewpage)
8. 고객이 예약했던 창고에 대해 후기를 남길 수 있다. 

비기능적 요구사항
1. 트랜잭션
    1. 결제가 되지 않은 예약 건은 성립되지 않아야 한다.  (Sync 호출)
1. 장애격리
    1. 창고 등록 및 메시지 전송 기능이 수행되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다  Async (event-driven), Eventual Consistency
    1. 예약 시스템이 과중되면 사용자를 잠시동안 받지 않고 잠시 후에 하도록 유도한다  Circuit breaker, fallback
1. 성능
    1. 모든 창고에 대한 정보 및 예약 상태 등을 한번에 확인할 수 있어야 한다  (CQRS)
    1. 예약의 상태가 바뀔 때마다 메시지로 알림을 줄 수 있어야 한다  (Event driven)


# 체크포인트

- 분석 설계

  - 이벤트스토밍: 
    - 스티커 색상별 객체의 의미를 제대로 이해하여 헥사고날 아키텍처와의 연계 설계에 적절히 반영하고 있는가?
    - 각 도메인 이벤트가 의미있는 수준으로 정의되었는가?
    - 어그리게잇: Command와 Event 들을 ACID 트랜잭션 단위의 Aggregate 로 제대로 묶었는가?
    - 기능적 요구사항과 비기능적 요구사항을 누락 없이 반영하였는가?    

  - 서브 도메인, 바운디드 컨텍스트 분리
    - 팀별 KPI 와 관심사, 상이한 배포주기 등에 따른  Sub-domain 이나 Bounded Context 를 적절히 분리하였고 그 분리 기준의 합리성이 충분히 설명되는가?
      - 적어도 3개 이상 서비스 분리
    - 폴리글랏 설계: 각 마이크로 서비스들의 구현 목표와 기능 특성에 따른 각자의 기술 Stack 과 저장소 구조를 다양하게 채택하여 설계하였는가?
    - 서비스 시나리오 중 ACID 트랜잭션이 크리티컬한 Use 케이스에 대하여 무리하게 서비스가 과다하게 조밀히 분리되지 않았는가?
  - 컨텍스트 매핑 / 이벤트 드리븐 아키텍처 
    - 업무 중요성과  도메인간 서열을 구분할 수 있는가? (Core, Supporting, General Domain)
    - Request-Response 방식과 이벤트 드리븐 방식을 구분하여 설계할 수 있는가?
    - 장애격리: 서포팅 서비스를 제거 하여도 기존 서비스에 영향이 없도록 설계하였는가?
    - 신규 서비스를 추가 하였을때 기존 서비스의 데이터베이스에 영향이 없도록 설계(열려있는 아키택처)할 수 있는가?
    - 이벤트와 폴리시를 연결하기 위한 Correlation-key 연결을 제대로 설계하였는가?

  - 헥사고날 아키텍처
    - 설계 결과에 따른 헥사고날 아키텍처 다이어그램을 제대로 그렸는가?
    
- 구현
  - [DDD] 분석단계에서의 스티커별 색상과 헥사고날 아키텍처에 따라 구현체가 매핑되게 개발되었는가?
    - Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 데이터 접근 어댑터를 개발하였는가
    - [헥사고날 아키텍처] REST Inbound adaptor 이외에 gRPC 등의 Inbound Adaptor 를 추가함에 있어서 도메인 모델의 손상을 주지 않고 새로운 프로토콜에 기존 구현체를 적응시킬 수 있는가?
    - 분석단계에서의 유비쿼터스 랭귀지 (업무현장에서 쓰는 용어) 를 사용하여 소스코드가 서술되었는가?
  - Request-Response 방식의 서비스 중심 아키텍처 구현
    - 마이크로 서비스간 Request-Response 호출에 있어 대상 서비스를 어떠한 방식으로 찾아서 호출 하였는가? (Service Discovery, REST, FeignClient)
    - 서킷브레이커를 통하여  장애를 격리시킬 수 있는가?
  - 이벤트 드리븐 아키텍처의 구현
    - 카프카를 이용하여 PubSub 으로 하나 이상의 서비스가 연동되었는가?
    - Correlation-key:  각 이벤트 건 (메시지)가 어떠한 폴리시를 처리할때 어떤 건에 연결된 처리건인지를 구별하기 위한 Correlation-key 연결을 제대로 구현 하였는가?
    - Message Consumer 마이크로서비스가 장애상황에서 수신받지 못했던 기존 이벤트들을 다시 수신받아 처리하는가?
    - Scaling-out: Message Consumer 마이크로서비스의 Replica 를 추가했을때 중복없이 이벤트를 수신할 수 있는가
    - CQRS: Materialized View 를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이) 도 내 서비스의 화면 구성과 잦은 조회가 가능한가?

  - 폴리글랏 플로그래밍
    - 각 마이크로 서비스들이 하나이상의 각자의 기술 Stack 으로 구성되었는가?
    - 각 마이크로 서비스들이 각자의 저장소 구조를 자율적으로 채택하고 각자의 저장소 유형 (RDB, NoSQL, File System 등)을 선택하여 구현하였는가?
  - API 게이트웨이
    - API GW를 통하여 마이크로 서비스들의 집입점을 통일할 수 있는가?
    - 게이트웨이와 인증서버(OAuth), JWT 토큰 인증을 통하여 마이크로서비스들을 보호할 수 있는가?
- 운영
  - SLA 준수
    - 셀프힐링: Liveness Probe 를 통하여 어떠한 서비스의 health 상태가 지속적으로 저하됨에 따라 어떠한 임계치에서 pod 가 재생되는 것을 증명할 수 있는가?
    - 서킷브레이커, 레이트리밋 등을 통한 장애격리와 성능효율을 높힐 수 있는가?
    - 오토스케일러 (HPA) 를 설정하여 확장적 운영이 가능한가?
    - 모니터링, 앨럿팅: 
  - 무정지 운영 CI/CD (10)
    - Readiness Probe 의 설정과 Rolling update을 통하여 신규 버전이 완전히 서비스를 받을 수 있는 상태일때 신규버전의 서비스로 전환됨을 siege 등으로 증명 
    - Contract Test :  자동화된 경계 테스트를 통하여 구현 오류나 API 계약위반를 미리 차단 가능한가?


# 분석/설계

## AS-IS 조직 (Horizontally-Aligned)
 ![image](https://user-images.githubusercontent.com/86210580/122710021-5f43af80-d29a-11eb-8efb-5813e583bf93.png)





## TO-BE 조직 (Vertically-Aligned)  
 ![image](https://user-images.githubusercontent.com/86210580/122710096-8e5a2100-d29a-11eb-8eed-be99860c4150.png)







## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과:  http://www.msaez.io/#/storming/QtpQtDiH1Je3wad2QxZUJVvnLzO2/share/6f36e16efdf8c872da3855fedf7f3ea9


### 이벤트 도출

![image](https://user-images.githubusercontent.com/84304023/122713236-5950cd00-d2a0-11eb-8610-b845021180f8.png)


### 부적격 이벤트 탈락
![image](https://user-images.githubusercontent.com/84304023/122713329-7e454000-d2a0-11eb-807c-f96b54a2aa37.png)

    - 과정중 도출된 잘못된 도메인 이벤트들을 걸러내는 작업을 수행함
        - 등록시>StorageSelected, 예약시>Resevationindormed :  UI 의 이벤트이지, 업무적인 의미의 이벤트가 아니라서 제외

### 액터, 커맨드 부착하여 읽기 좋게
![image](https://user-images.githubusercontent.com/84304023/122713526-c6646280-d2a0-11eb-97d3-04d04314ccde.png)

### 어그리게잇으로 묶기
![image](https://user-images.githubusercontent.com/84304023/122714889-0a586700-d2a3-11eb-88e3-af7d4a8ee84c.png)


    - 창고(Storage), 예약(Reservation), 결제(Payment), 리뷰(Review) 은 그와 연결된 command 와 event 들에 의하여 트랜잭션이 유지되어야 하는 단위로 그들 끼리 묶어줌

### 바운디드 컨텍스트로 묶기
![image](https://user-images.githubusercontent.com/84304023/122714472-522abe80-d2a2-11eb-8756-3479aa73d505.png)



    - 도메인 서열 분리 
        - Core Domain:  reservation, storage : 없어서는 안될 핵심 서비스이며, 연간 Up-time SLA 수준을 99.999% 목표, 배포주기는 reservation 의 경우 1주일 1회 미만, storage 의 경우 1개월 1회 미만
        - Supporting Domain:   message, viewpage : 경쟁력을 내기위한 서비스이며, SLA 수준은 연간 60% 이상 uptime 목표, 배포주기는 각 팀의 자율이나 표준 스프린트 주기가 1주일 이므로 1주일 1회 이상을 기준으로 함.
        - General Domain:   payment : 결제서비스로 3rd Party 외부 서비스를 사용하는 것이 경쟁력이 높음 

### 폴리시 부착 (괄호는 수행주체, 폴리시 부착을 둘째단계에서 해놔도 상관 없음. 전체 연계가 초기에 드러남)
![image](https://user-images.githubusercontent.com/84304023/122714619-88683e00-d2a2-11eb-9800-66750eb6defd.png)

### 폴리시의 이동과 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Resp)

![image](https://user-images.githubusercontent.com/84304023/122714837-f3197980-d2a2-11eb-89c8-3dbd182114a9.png)

### 완성된 1차 모형

![image](https://user-images.githubusercontent.com/86210580/122715446-d6ca0c80-d2a3-11eb-99e8-0ab788d1d1dc.png)

    - View Model 추가

### 1차 완성본에 대한 기능적/비기능적 요구사항을 커버하는지 검증

![image](https://user-images.githubusercontent.com/86210580/122716388-2826cb80-d2a5-11eb-937f-60958e97994a.png)


    - 관리자가 임대할 창고를 등록/수정/삭제한다.(ok) -> Puple line
    - 고객이 창고를 선택하여 예약한다.(ok) -> Green line
    - 예약과 동시에 결제가 진행된다.(ok) -> Green line
    - 예약이 되면 예약 내역(Message)이 전달된다.(?)
    - 고객이 예약을 취소할 수 있다.(ok) -> Red line
    - 예약 사항이 취소될 경우 취소 내역(Message)이 전달된다.(?)
    - 창고에 대한 후기(review)를 남길 수 있다.(ok) -> Puple line
    - 전체적인 창고에 대한 정보 및 예약 상태 등을 한 화면에서 확인 할 수 있다.(View-green Sticker 추가로 ok)
    
### 모델 수정

![image](https://user-images.githubusercontent.com/86210580/122729232-e5202480-d2b3-11eb-90f3-c612cebbea5d.png)

    
    - 수정된 모델은 모든 요구사항을 커버함.

### 비기능 요구사항에 대한 검증

![image](https://user-images.githubusercontent.com/84304023/122727848-74c4d380-d2b2-11eb-8e3a-c35d2f672ea9.png)

- 마이크로 서비스를 넘나드는 시나리오에 대한 트랜잭션 처리
- 고객 예약시 결제처리:  결제가 완료되지 않은 예약은 절대 받지 않는다고 결정하여, ACID 트랜잭션 적용. 예약 완료시 사전에 창고 상태를 확인하는 것과 결제처리에 대해서는 Request-Response 방식 처리 (1)
- 결제 완료시 manager 연결 및 예약처리:  reservation 에서 storage 마이크로서비스로 예약요청이 전달되는 과정에 있어서 storage 마이크로 서비스가 별도의 배포주기를 가지기 때문에 Eventual Consistency 방식으로 트랜잭션 처리함. (2)
- 나머지 모든 inter-microservice 트랜잭션: 예약상태, 후기처리 등 모든 이벤트에 대해 데이터 일관성의 시점이 크리티컬하지 않은 모든 경우가 대부분이라 판단, Eventual Consistency 를 기본으로 채택함.(3)


## 헥사고날 아키텍처 다이어그램 도출

![image](https://user-images.githubusercontent.com/84304043/122707112-4d5f0e00-d294-11eb-8157-0b102dfad907.png)

    - Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
    - 호출관계에서 PubSub 과 Req/Resp 를 구분함
    - 서브 도메인과 바운디드 컨텍스트의 분리:  각 팀의 KPI 별로 아래와 같이 관심 구현 스토리를 나눠가짐


# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)

```
   mvn spring-boot:run
```

## CQRS

창고(Storage) 의 사용가능 여부, 리뷰 및 예약/결재 등 총 Status 에 대하여 고객(Customer)이 조회 할 수 있도록 CQRS 로 구현하였다.
- storage, review, reservation, payment 개별 Aggregate Status 를 통합 조회하여 성능 Issue 를 사전에 예방할 수 있다.
- 비동기식으로 처리되어 발행된 이벤트 기반 Kafka 를 통해 수신/처리 되어 별도 Table 에 관리한다
- Table 모델링 (StorageView)
 
  ![image](https://user-images.githubusercontent.com/84304043/122712630-2fe37180-d29f-11eb-9ad4-1bfe12fbeb25.png)
 
- viewpage MSA ViewHandler 를 통해 구현 ("StorageRegistered" 이벤트 발생 시, Pub/Sub 기반으로 별도 Storageview 테이블에 저장)
 ![image](https://user-images.githubusercontent.com/84304043/122714557-6f5f8d00-d2a2-11eb-9e82-d811f3e1ab3e.png)
 ![image](https://user-images.githubusercontent.com/84304043/122714566-71c1e700-d2a2-11eb-92e9-51ef24aa46ae.png)
 
- 실제로 view 페이지를 조회해 보면 모든 storage에 대한 전반적인 예약 상태, 결제 상태, 리뷰 건수 등의 정보를 종합적으로 알 수 있다
```
   http GET localhost:8088/storageviews
```
![뷰](https://user-images.githubusercontent.com/84304043/122842865-227ac580-d339-11eb-906e-2873cd375b04.PNG)


## API 게이트웨이
      1. gateway 스프링부트 App을 추가 후 application.yaml내에 각 마이크로 서비스의 routes 를 추가하고 gateway 서버의 포트를 8080 으로 설정함
       
          - application.yml 예시
            ```
	spring:
	  profiles: docker
	  cloud:
	    gateway:
	      routes:
		- id: payment
		  uri: http://payment:8081
		  predicates:
		    - Path=/payments/** 
		- id: storage
		  uri: http://storage:8082
		  predicates:
		    - Path=/storages/**, /reviews/**, /check/**
		- id: reservation
		  uri: http://reservation:8083
		  predicates:
		    - Path=/reservations/** 
		- id: message
		  uri: http://message:8084
		  predicates:
		    - Path=/messages/** 
		- id: viewpage
		  uri: http://viewpage:8085
		  predicates:
		    - Path= /storageviews/**
	      globalcors:
		corsConfigurations:
		  '[/**]':
		    allowedOrigins:
		      - "*"
		    allowedMethods:
		      - "*"
		    allowedHeaders:
		      - "*"
		    allowCredentials: true

	server:
	  port: 8080       
            ```

         
      2. Kubernetes용 Deployment.yaml 을 작성하고 Kubernetes에 Deploy를 생성함
          - Deployment.yaml 예시
          

            ```
	apiVersion: apps/v1
	kind: Deployment
	metadata:
	  name: gateway
	  namespace: storagerent
	  labels:
	    app: gateway
	spec:
	  replicas: 1
	  selector:
	    matchLabels:
	      app: gateway
	  template:
	    metadata:
	      labels:
		app: gateway
	    spec:
	      containers:
		- name: gateway
		  image: 740569282574.dkr.ecr.ap-northeast-1.amazonaws.com/user02-gateway:v1
		  ports:
		    - containerPort: 8080
            ```               
            

            ```
            Deploy 생성
            kubectl apply -f deployment.yaml
            ```     
          - Kubernetes에 생성된 Deploy. 확인
            
![image](https://user-images.githubusercontent.com/84304043/122843390-4d194e00-d33a-11eb-82b9-d156fce642d0.png)
	    
            
      3. Kubernetes용 Service.yaml을 작성하고 Kubernetes에 Service/LoadBalancer을 생성하여 Gateway 엔드포인트를 확인함. 
          - Service.yaml 예시
          
            ```
            apiVersion: v1
              kind: Service
              metadata:
                name: gateway
                namespace: storagerent
                labels:
                  app: gateway
              spec:
                ports:
                  - port: 8080
                    targetPort: 8080
                selector:
                  app: gateway
                type:
                  LoadBalancer           
            ```             

           
            ```
            Service 생성
            kubectl apply -f service.yaml            
            ```             
            
            
          - API Gateay 엔드포인트 확인
           
            ```
            Service  및 엔드포인트 확인 
            kubectl get svc -n storagerent           
            ```                

![image](https://user-images.githubusercontent.com/84304043/122770160-229aa700-d2e0-11eb-9f85-b6fcb8cabe0e.png)


# Correlation

창고대여 프로젝트에서는 PolicyHandler에서 처리 시 어떤 건에 대한 처리인지를 구별하기 위한 Correlation-key 구현을 
이벤트 클래스 안의 변수로 전달받아 서비스간 연관된 처리를 정확하게 구현하고 있습니다. 

아래의 구현 예제를 보면

예약(Reservation)을 하면 동시에 연관된 창고(Storage), 결제(Payment) 등의 서비스의 상태가 적당하게 변경이 되고,
예약건의 취소를 수행하면 다시 연관된 창고(Storage), 결제(Payment) 등의 서비스의 상태값 등의 데이터가 적당한 상태로 변경되는 것을
확인할 수 있습니다.

- 창고등록
```
http POST http://localhost:8088/storages description="BigStorage" price=200000 storageStatus="available"
```  
![image](https://user-images.githubusercontent.com/84304043/122844125-eb59e380-d33b-11eb-9a85-1a892021ca0d.png)
- 예약등록
```
http POST localhost:8088/reservations storageId=1 price=200000 reservationStatus="reqReserve"
```  
![image](https://user-images.githubusercontent.com/84304043/122843690-0415c980-d33b-11eb-9558-c423faa1bd42.png)
- 예약 후 - 창고 상태
```
http GET http://localhost:8088/storages/1
```  
![image](https://user-images.githubusercontent.com/84304043/122843724-1d1e7a80-d33b-11eb-8a52-8b7f772df2e3.png)
- 예약 후 - 예약 상태
```
http GET http://localhost:8088/reservations/1
```  
![image](https://user-images.githubusercontent.com/84304043/122843763-31fb0e00-d33b-11eb-83f6-140191ec1a6d.png)
- 예약 후 - 결제 상태
```
http GET http://localhost:8088/payments/1
``` 
![image](https://user-images.githubusercontent.com/84304043/122843798-43441a80-d33b-11eb-92c4-160c77f6f3ef.png)
- 예약 취소
```
http PATCH localhost:8088/reservations/1 storageId=1 price=200000 reservationStatus="reqCancel"
``` 
![image](https://user-images.githubusercontent.com/84304043/122843840-57881780-d33b-11eb-88fe-61d8055ff1e0.png)
- 예약 취소 후 - 창고 상태
```
http GET http://localhost:8088/storages/1
``` 
![image](https://user-images.githubusercontent.com/84304043/122843892-6ec70500-d33b-11eb-9663-e4c894dff60b.png)
- 예약 취소 후 - 예약 상태
```
http GET http://localhost:8088/reservations/1
``` 
![image](https://user-images.githubusercontent.com/84304043/122843932-856d5c00-d33b-11eb-88a9-921c14d97ed0.png)
- 예약 취소 후 - 결제 상태
```
http GET http://localhost:8088/payments/1
``` 
![image](https://user-images.githubusercontent.com/84304043/122843963-95853b80-d33b-11eb-8e0a-4831fa73a5b4.png)


## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다. (예시는 storage 마이크로 서비스). 이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용하려고 노력했다. 현실에서 발생가능한 이벤트에 의하여 마이크로 서비스들이 상호 작용하기 좋은 모델링으로 구현을 하였다.

```
package storagerent;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

@Entity
@Table(name="Storage_table")
public class Storage {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long storageId;
    private String storageStatus;
    private String description;
    private Long reviewCnt;
    private String lastAction;
    private Float price;

    @PostPersist
    public void onPostPersist(){
        StorageRegistered storageRegistered = new StorageRegistered();
        BeanUtils.copyProperties(this, storageRegistered);
        storageRegistered.publishAfterCommit();
    }

    @PostUpdate
    public void onPostUpdate(){
        if("modify".equals(lastAction) || "review".equals(lastAction)) {
            StorageModified storageModified = new StorageModified();
            BeanUtils.copyProperties(this, storageModified);
            storageModified.publishAfterCommit();
        }
        if("reserved".equals(lastAction)) {
            StorageReserved storageReserved = new StorageReserved();
            BeanUtils.copyProperties(this, storageReserved);
            storageReserved.publishAfterCommit();
        }
        if("cancelled".equals(lastAction)) {
            StorageCancelled storageCancelled = new StorageCancelled();
            BeanUtils.copyProperties(this, storageCancelled);
            storageCancelled.publishAfterCommit();
        }
    }

    @PreRemove
    public void onPreRemove(){
        StorageDeleted storageDeleted = new StorageDeleted();
        BeanUtils.copyProperties(this, storageDeleted);
        storageDeleted.publishAfterCommit();
    }
    public Long getStorageId() {
        return storageId;
    }
    public void setStorageId(Long storageId) {
        this.storageId = storageId;
    }
    public String getStorageStatus() {
        return storageStatus;
    }
    public void setStorageStatus(String storageStatus) {
        this.storageStatus = storageStatus;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public Long getReviewCnt() {
        return reviewCnt;
    }
    public void setReviewCnt(Long reviewCnt) {
        this.reviewCnt = reviewCnt;
    }
    public String getLastAction() {
        return lastAction;
    }
    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }
    public Float getPrice() {
        return price;
    }
    public void setPrice(Float price) {
        this.price = price;
    }
}


```
- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package storagerent;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="storages", path="storages")
public interface StorageRepository extends PagingAndSortingRepository<Storage, Long>{

}
```
- 적용 후 REST API 의 테스트
```
# storage 서비스의 대여창고 등록
http POST http://localhost:8088/storages description="storage1" price=200000 storageStatus="available"
  
# reservation 서비스의 창고 예약 요청
http POST http:localhost:8088/reservations storageId=1 price=200000 reservationStatus="reqReserve"

# reservation 서비스의 예약 상태 확인
http GET http://localhost:8088/reservations/1

```

## 동기식 호출(Sync) 과 Fallback 처리

분석 단계에서의 조건 중 하나로 예약 시 창고(storage) 간의 예약 가능 상태 확인 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 또한 예약(reservation) -> 결제(payment) 서비스도 동기식으로 처리하기로 하였다.

- 창고, 결제 서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```
# PaymentService.java

package storagerent.external;

<import문 생략>

@FeignClient(name="payment", url="${prop.payment.url}")
public interface PaymentService {
    @RequestMapping(method= RequestMethod.POST, path="/payments")
    public void approvePayment(@RequestBody Payment payment);
}

# StorageService.java

package storagerent.external;

<import문 생략>

@FeignClient(name="Storage", url="${prop.storage.url}")
public interface StorageService {

    @RequestMapping(method=RequestMethod.GET, path="/check/chkAndReqReserve")
    public boolean chkAndReqReserve(@RequestParam("storageId") long storageId);
}

```

- 예약 요청을 받은 직후(@PostPersist) 가능상태 확인 및 결제를 동기(Sync)로 요청하도록 처리
```
# Reservation.java (Entity)

    @PostPersist
    public void onPostPersist(){
    
        //------------------
        // 예약이 들어온 경우
        //------------------

        // 해당 Storage가 Available한 상태인지 체크
       boolean result = ReservationApplication.applicationContext.getBean(storagerent.external.StorageService.class).chkAndReqReserve(this.getStorageId());
        System.out.println("######## Storage Available Check Result : " + result);
        
        if(result) { 

            // 예약 가능한 상태인 경우(Available)

            //----------------------------
            // PAYMENT 결제 진행 (POST방식)
            //----------------------------
            storagerent.external.Payment payment = new storagerent.external.Payment();
            payment.setReservationId(this.getReservationId());
            payment.setStorageId(this.getStorageId());
            payment.setPaymentStatus("paid");
            payment.setPrice(this.price);
            ReservationApplication.applicationContext.getBean(storagerent.external.PaymentService.class)
                .approvePayment(payment);

            //----------------------------------
            // 이벤트 발행 --> ReservationCreated
            //----------------------------------
            ReservationCreated reservationCreated = new ReservationCreated();
            BeanUtils.copyProperties(this, reservationCreated);
            reservationCreated.publishAfterCommit();
        }
    }
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인:


```
# 결제 (pay) 서비스를 잠시 내려놓음 (ctrl+c)

# 대여창고 등록
http POST http://localhost:8088/storages description="storage1" price=200000 storageStatus="available"

# Payment 서비스 종료 후 창고대여
http POST localhost:8088/reservations storageId=1 price=200000 reservationStatus="reqReserve"

# Payment 서비스 실행 후 창고대여
http POST localhost:8088/reservations storageId=1 price=200000 reservationStatus="reqReserve"

# 창고대여 확인 
http GET http://localhost:8088/reservations/1  
```

- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, 폴백 처리는 운영단계에서 설명한다.)




## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트


결제가 이루어진 후에 숙소 시스템의 상태가 업데이트 되고, 예약 시스템의 상태가 업데이트 되며, 예약 및 취소 메시지가 전송되는 시스템과의 통신 행위는 비동기식으로 처리한다.
 
- 이를 위하여 결제가 승인되면 결제가 승인 되었다는 이벤트를 카프카로 송출한다. (Publish)
 
```
# Payment.java

package storagerent;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

@Entity
@Table(name="Payment_table")
public class Payment {

    ....

    @PostPersist
    public void onPostPersist(){
        ////////////////////////////
        // 결제 승인 된 경우
        ////////////////////////////

        // 이벤트 발행 -> PaymentApproved
        PaymentApproved paymentApproved = new PaymentApproved();
        BeanUtils.copyProperties(this, paymentApproved);
        paymentApproved.publishAfterCommit();
    }
    
    ....
}
```

- 예약 시스템에서는 결제 승인 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:

```
# Reservation.java

package storagerent;

    @PostUpdate
    public void onPostUpdate(){
    
        ....

        if("reserved".equals(this.getReservationStatus())) {

            ////////////////////
            // 예약 확정된 경우
            ////////////////////

            // 이벤트 발생 --> ReservationConfirmed
            ReservationConfirmed reservationConfirmed = new ReservationConfirmed();
            BeanUtils.copyProperties(this, reservationConfirmed);
            reservationConfirmed.publishAfterCommit();
        }
        
        ....
        
    }

```

그 외 메시지 서비스는 예약/결제와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 메시지 서비스가 유지보수로 인해 잠시 내려간 상태 라도 예약을 받는데 문제가 없다.

```
# 메시지 서비스 (message) 를 잠시 내려놓음 (ctrl+c)

# 대여창고 등록
http POST http://localhost:8088/storages description="msg1" price=200000 storageStatus="available"

# Message 서비스 종료 후 창고대여
http POST localhost:8088/reservations storageId=5 price=200000 reservationStatus="reqReserve"   

# Message 서비스와 상관없이 창고대여 성공여부 확인
http GET http://localhost:8088/reservations/2

```
## 폴리그랏 퍼시스턴스 적용
```
Message Sevices : hsqldb사용
```
![image](https://user-images.githubusercontent.com/84304043/122845081-dda55d80-d33d-11eb-8d9f-a4e17735574e.png)
```
Message이외  Sevices : h2db사용
```
![image](https://user-images.githubusercontent.com/84304043/122845106-ed24a680-d33d-11eb-9124-aed5d9e7285b.png)

## Maven 빌드시스템 라이브러리 추가( pom.xml 설정변경 H2DB → HSQLDB) 
![image](https://user-images.githubusercontent.com/84304043/122845179-0fb6bf80-d33e-11eb-879a-1e6e8964ebb3.png)

# 운영


## CI/CD 설정

각 구현체들은 각자의 source repository 에 구성되었고, 사용한 CI/CD는 buildspec.yml을 이용한 AWS codebuild를 사용하였습니다.

- CodeBuild 프로젝트를 생성하고 AWS_ACCOUNT_ID, KUBE_URL, KUBE_TOKEN 환경 변수 세팅을 한다
```
SA 생성
kubectl apply -f eks-admin-service-account.yml
```
![image](https://user-images.githubusercontent.com/84304043/122844500-c154f100-d33c-11eb-9ec0-5eb0fa3540d6.png)
```
Role 생성
kubectl apply -f eks-admin-cluster-role-binding.yml
```
![image](https://user-images.githubusercontent.com/84304043/122844538-d6ca1b00-d33c-11eb-818b-5a51404265c1.png)
```
Token 확인
kubectl -n kube-system get secret
kubectl -n kube-system describe secret $(kubectl -n kube-system get secret | grep eks-admin | awk '{print $1}')
```
![image](https://user-images.githubusercontent.com/86210580/122849832-34fbfb80-d347-11eb-9f6d-b1e379b3e1cf.png)

```
buildspec.yml 파일 
마이크로 서비스 storage의 yml 파일 이용하도록 세팅
```
![image](https://user-images.githubusercontent.com/84304043/122844673-201a6a80-d33d-11eb-8a52-a0fad02951d9.png)

- codebuild 실행
```
codebuild 프로젝트 및 빌드 이력
```
![image](https://user-images.githubusercontent.com/84304043/122846416-bdc36900-d340-11eb-9558-cad08d2615f2.png)
![image](https://user-images.githubusercontent.com/84304043/122861596-a2fdee00-d35a-11eb-9d73-7ff537c9e332.png)

- codebuild 빌드 내역 (Message 서비스 세부)

![image](https://user-images.githubusercontent.com/84304043/122846449-cd42b200-d340-11eb-8a33-aeff63915d61.png)

- codebuild 빌드 내역 (전체 이력 조회)

![image](https://user-images.githubusercontent.com/84304043/122846462-d5025680-d340-11eb-9914-b12b82a74ff5.png)



## 동기식 호출 / 서킷 브레이킹 / 장애격리

* 서킷 브레이킹: Hystrix 사용하여 구현함

시나리오는 예약(reservation)--> 창고(storage) 시의 연결을 RESTful Request/Response 로 연동하여 구현이 되어있고, 예약 요청이 과도할 경우 CB 를 통하여 장애격리.

- DestinationRule 를 생성하여 circuit break 가 발생할 수 있도록 설정
최소 connection pool 설정
```
# destination-rule.yml
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: dr-storage
  namespace: storagerent
spec:
  host: storage
  trafficPolicy:
    connectionPool:
      http:
        http1MaxPendingRequests: 1
        maxRequestsPerConnection: 1
#    outlierDetection:
#      interval: 1s
#      consecutiveErrors: 1
#      baseEjectionTime: 10s
#      maxEjectionPercent: 100
```


* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:

siege 실행

```
kubectl run siege --image=apexacme/siege-nginx -n storagerent
kubectl exec -it siege -c siege -n storagerent -- /bin/bash
```

- 동시사용자 1로 부하 생성 시 모두 정상
```
siege -c1 -t10S -v --content-type "application/json" 'http://storage:8080/storages POST {"desc": "BigStorage"}'

** SIEGE 4.0.4
** Preparing 1 concurrent users for battle.
The server is now under siege...
HTTP/1.1 201     0.49 secs:     254 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.05 secs:     254 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     254 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.03 secs:     254 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     254 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     254 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.03 secs:     254 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.03 secs:     254 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.03 secs:     254 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.03 secs:     256 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.03 secs:     256 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     256 bytes ==> POST http://storage:8080/storages
```

- 동시사용자 2로 부하 생성 시 503 에러 168개 발생
```
siege -c2 -t10S -v --content-type "application/json" 'http://storage:8080/storages POST {"desc": "Beautiful House3"}'

** SIEGE 4.0.4
** Preparing 2 concurrent users for battle.
The server is now under siege...
HTTP/1.1 201     0.02 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 503     0.10 secs:      81 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.04 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.05 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.22 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.08 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.07 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 503     0.01 secs:      81 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.03 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 503     0.01 secs:      81 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     258 bytes ==> POST http://storage:8080/storages
HTTP/1.1 503     0.00 secs:      81 bytes ==> POST http://storage:8080/storages

Lifting the server siege...
Transactions:                   1904 hits
Availability:                  91.89 %
Elapsed time:                   9.89 secs
Data transferred:               0.48 MB
Response time:                  0.01 secs
Transaction rate:             192.52 trans/sec
Throughput:                     0.05 MB/sec
Concurrency:                    1.98
Successful transactions:        1904
Failed transactions:             168
Longest transaction:            0.03
Shortest transaction:           0.00
```


- 다시 최소 Connection pool로 부하 다시 정상 확인

```
** SIEGE 4.0.4
** Preparing 1 concurrent users for battle.
The server is now under siege...
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.03 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.00 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.02 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.00 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.00 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storages

:
:

Lifting the server siege...
Transactions:                   1139 hits
Availability:                 100.00 %
Elapsed time:                   9.19 secs
Data transferred:               0.28 MB
Response time:                  0.01 secs
Transaction rate:             123.94 trans/sec
Throughput:                     0.03 MB/sec
Concurrency:                    0.98
Successful transactions:        1139
Failed transactions:               0
Longest transaction:            0.04
Shortest transaction:           0.00

```

- 운영시스템은 죽지 않고 지속적으로 CB 에 의하여 적절히 회로가 열림과 닫힘이 벌어지면서 자원을 보호하고 있음을 보여줌.
  virtualhost 설정과 동적 Scale out (replica의 자동적 추가,HPA) 을 통하여 시스템을 확장 해주는 후속처리가 필요.


### 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 

- storage deployment.yml 파일에 resources 설정을 추가한다

![image](https://user-images.githubusercontent.com/84304043/122850814-d6378180-d348-11eb-9cd2-eb0873f1c8d7.png)

- storage 서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 50프로를 넘어서면 replica 를 10개까지 늘려준다:
```
kubectl autoscale deployment storage -n storagerent --cpu-percent=50 --min=1 --max=10
```

- 부하를 동시사용자 100명, 1분 동안 걸어준다.
```
siege -c100 -t60S -v --content-type "application/json" 'http://storage:8080/storages POST {"desc": "BigStorage"}'
```
- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다
```
kubectl get deploy storage -w -n storagerent 
```
- 어느정도 시간이 흐른 후 (약 30초) 스케일 아웃이 벌어지는 것을 확인할 수 있다:

- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다. 
```
Lifting the server siege...
Transactions:                  15615 hits
Availability:                 100.00 %
Elapsed time:                  59.44 secs
Data transferred:               3.90 MB
Response time:                  0.32 secs
Transaction rate:             262.70 trans/sec
Throughput:                     0.07 MB/sec
Concurrency:                   85.04
Successful transactions:       15675
Failed transactions:               0
Longest transaction:            2.55
Shortest transaction:           0.01
```

## 무정지 재배포

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

```
kubectl delete destinationrules dr-storage -n storagerent
kubectl delete hpa storage -n storagerent
```

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
siege -c100 -t60S -r10 -v --content-type "application/json" 'http://storage:8080/storages POST {"desc": "BigStorage"}'

** SIEGE 4.0.4
** Preparing 1 concurrent users for battle.
The server is now under siege...
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storags
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storags
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storags
HTTP/1.1 201     0.03 secs:     260 bytes ==> POST http://storage:8080/storags
HTTP/1.1 201     0.00 secs:     260 bytes ==> POST http://storage:8080/storags
HTTP/1.1 201     0.02 secs:     260 bytes ==> POST http://storage:8080/storags
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storags
HTTP/1.1 201     0.01 secs:     260 bytes ==> POST http://storage:8080/storags

```

- 새버전으로의 배포 시작
```
kubectl set image ...
```

- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인

```
siege -c100 -t60S -r10 -v --content-type "application/json" 'http://storage:8080/storages POST {"desc": "BigStorage"}'


Transactions:                   7732 hits
Availability:                  87.32 %
Elapsed time:                  17.12 secs
Data transferred:               1.93 MB
Response time:                  0.18 secs
Transaction rate:             451.64 trans/sec
Throughput:                     0.11 MB/sec
Concurrency:                   81.21
Successful transactions:        7732
Failed transactions:            1123
Longest transaction:            0.94
Shortest transaction:           0.00

```
- 배포기간중 Availability 가 평소 100%에서 87% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 이를 막기위해 Readiness Probe 를 설정함

```
# deployment.yaml 의 readiness probe 의 설정:
```

![image](https://user-images.githubusercontent.com/84304043/122858339-156bcf80-d355-11eb-9d1a-91da438ac905.png)


```
kubectl apply -f kubernetes/deployment.yml
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:
```
Lifting the server siege...
Transactions:                  27657 hits
Availability:                 100.00 %
Elapsed time:                  59.41 secs
Data transferred:               6.91 MB
Response time:                  0.21 secs
Transaction rate:             465.53 trans/sec
Throughput:                     0.12 MB/sec
Concurrency:                   99.60
Successful transactions:       27657
Failed transactions:               0
Longest transaction:            1.20
Shortest transaction:           0.00

```

배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.


# Self-healing (Liveness Probe)
- storage deployment.yml 파일 수정 
```
콘테이너 실행 후 /tmp/healthy 파일을 만들고 
90초 후 삭제
livenessProbe에 'cat /tmp/healthy'으로 검증하도록 함
```
![image](https://user-images.githubusercontent.com/84304043/122863309-80210900-d35d-11eb-8e07-8113c4ca6af9.png)

- kubectl describe pod storage -n storagerent 실행으로 확인
```
컨테이너 실행 후 90초 동인은 정상이나 이후 /tmp/healthy 파일이 삭제되어 livenessProbe에서 실패를 리턴하게 됨
pod 정상 상태 일때 pod 진입하여 /tmp/healthy 파일 생성해주면 정상 상태 유지됨
```

# Config Map/ Persistence Volume
- Persistence Volume

1: EFS 생성
```
EFS 생성 시 클러스터의 VPC를 선택해야함
```
![클러스터의 VPC를 선택해야함](https://user-images.githubusercontent.com/38099203/119364089-85048580-bce9-11eb-8001-1c20a93b8e36.PNG)

![EFS생성](https://user-images.githubusercontent.com/38099203/119343415-60041880-bcd1-11eb-9c25-1695c858f6aa.PNG)

2. EFS 계정 생성 및 ROLE 바인딩
```
kubectl apply -f efs-sa.yml

apiVersion: v1
kind: ServiceAccount
metadata:
  name: efs-provisioner
  namespace: storagerent


kubectl get ServiceAccount efs-provisioner -n airbnb
NAME              SECRETS   AGE
efs-provisioner   1         9m1s  
  
  
  
kubectl apply -f efs-rbac.yaml

namespace를 반드시 수정해야함

  
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: efs-provisioner-runner
  namespace: storagerent
rules:
  - apiGroups: [""]
    resources: ["persistentvolumes"]
    verbs: ["get", "list", "watch", "create", "delete"]
  - apiGroups: [""]
    resources: ["persistentvolumeclaims"]
    verbs: ["get", "list", "watch", "update"]
  - apiGroups: ["storage.k8s.io"]
    resources: ["storageclasses"]
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources: ["events"]
    verbs: ["create", "update", "patch"]
---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: run-efs-provisioner
  namespace: storagerent
subjects:
  - kind: ServiceAccount
    name: efs-provisioner
     # replace with namespace where provisioner is deployed
    namespace: storagerent
roleRef:
  kind: ClusterRole
  name: efs-provisioner-runner
  apiGroup: rbac.authorization.k8s.io
---
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: leader-locking-efs-provisioner
  namespace: storagerent
rules:
  - apiGroups: [""]
    resources: ["endpoints"]
    verbs: ["get", "list", "watch", "create", "update", "patch"]
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: leader-locking-efs-provisioner
  namespace: storagerent
subjects:
  - kind: ServiceAccount
    name: efs-provisioner
    # replace with namespace where provisioner is deployed
    namespace: storagerent
roleRef:
  kind: Role
  name: leader-locking-efs-provisioner
  apiGroup: rbac.authorization.k8s.io


```

3. EFS Provisioner 배포
```
kubectl apply -f efs-provisioner-deploy.yml

apiVersion: apps/v1
kind: Deployment
metadata:
  name: efs-provisioner
  namespace: storagerent
spec:
  replicas: 1
  strategy:
    type: Recreate
  selector:
    matchLabels:
      app: efs-provisioner
  template:
    metadata:
      labels:
        app: efs-provisioner
    spec:
      serviceAccount: efs-provisioner
      containers:
        - name: efs-provisioner
          image: quay.io/external_storage/efs-provisioner:latest
          env:
            - name: FILE_SYSTEM_ID
              value: fs-562f9c36
            - name: AWS_REGION
              value: ap-northeast-2
            - name: PROVISIONER_NAME
              value: my-aws.com/aws-efs
          volumeMounts:
            - name: pv-volume
              mountPath: /persistentvolumes
      volumes:
        - name: pv-volume
          nfs:
            server: fs-562f9c36.efs.ap-northeast-2.amazonaws.com
            path: /


kubectl get Deployment efs-provisioner -n airbnb
NAME              READY   UP-TO-DATE   AVAILABLE   AGE
efs-provisioner   1/1     1            1           11m

```

4. 설치한 Provisioner를 storageclass에 등록
```
kubectl apply -f efs-storageclass.yml


kind: StorageClass
apiVersion: storage.k8s.io/v1
metadata:
  name: aws-efs
  namespace: storagerent
provisioner: my-aws.com/aws-efs


kubectl get sc aws-efs -n storagerent
NAME            PROVISIONER             RECLAIMPOLICY   VOLUMEBINDINGMODE      ALLOWVOLUMEEXPANSION   AGE
aws-efs         my-aws.com/aws-efs      Delete          Immediate              false                  4s
```

5. PVC(PersistentVolumeClaim) 생성
```
kubectl apply -f volume-pvc.yml


apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: aws-efs
  namespace: storagerent
  labels:
    app: test-pvc
spec:
  accessModes:
  - ReadWriteMany
  resources:
    requests:
      storage: 6Ki
  storageClassName: aws-efs
  
  
kubectl get pvc aws-efs -n storagerent
NAME      STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
aws-efs   Bound    pvc-43f6fe12-b9f3-400c-ba20-b357c1639f00   6Ki        RWX            aws-efs        4m44s
```

6. storage pod 적용
```
kubectl apply -f deployment.yml
```
![pod with pvc](https://user-images.githubusercontent.com/38099203/119349966-bd9c6300-bcd9-11eb-9f6d-08e4a3ec82f0.PNG)


7. A pod에서 마운트된 경로에 파일을 생성하고 B pod에서 파일을 확인함
```
NAME                              READY   STATUS    RESTARTS   AGE
efs-provisioner-f4f7b5d64-lt7rz   1/1     Running   0          14m
storage-5df66d6674-n6b7n             1/1     Running   0          109s
storage-5df66d6674-pl25l             1/1     Running   0          109s
siege                             1/1     Running   0          2d1h


kubectl exec -it pod/storage-5df66d6674-n6b7n storage -n storagerent -- /bin/sh
/ # cd /mnt/aws
/mnt/aws # touch intensive_course_work
```
![a pod에서 파일생성](https://user-images.githubusercontent.com/38099203/119372712-9736f180-bcf2-11eb-8e57-1d6e3f4273a5.PNG)

```
kubectl exec -it pod/storage-5df66d6674-pl25l storage -n storagerent -- /bin/sh
/ # cd /mnt/aws
/mnt/aws # ls -al
total 8
drwxrws--x    2 root     2000          6144 May 24 15:44 .
drwxr-xr-x    1 root     root            17 May 24 15:42 ..
-rw-r--r--    1 root     2000             0 May 24 15:44 intensive_course_work
```
![b pod에서 파일생성 확인](https://user-images.githubusercontent.com/38099203/119373196-204e2880-bcf3-11eb-88f0-a1e91a89088a.PNG)


- Config Map

1: cofingmap.yml 파일 생성
```
kubectl apply -f cofingmap.yml


apiVersion: v1
kind: ConfigMap
metadata:
  name: storagerent-config
  namespace: storagerent
data:
  # 단일 key-value
  max_reservation_per_person: "10"
  ui_properties_file_name: "user-interface.properties"
```

2. deployment.yml에 적용하기

```
kubectl apply -f deployment.yml


.......
          env:
			# cofingmap에 있는 단일 key-value
            - name: MAX_RESERVATION_PER_PERSION
              valueFrom:
                configMapKeyRef:
                  name: storagerent-config
                  key: max_reservation_per_person
           - name: UI_PROPERTIES_FILE_NAME
              valueFrom:
                configMapKeyRef:
                  name: storagerent-config
                  key: ui_properties_file_name
          volumeMounts:
          - mountPath: "/mnt/aws"
            name: volume
      volumes:
        - name: volume
          persistentVolumeClaim:
            claimName: aws-efs
```

