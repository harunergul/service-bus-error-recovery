# Patient Location Tracking

Records patients being moved between rooms and broadcasts each move to interested services, as a learning ground for message-bus reliability.

## Language

### Patients and moves

**Patient**:
A person registered in the system, identified by a system-assigned patient id and described by a name and a National Identity.

**National Identity**:
The government-issued identity number of a Patient, supplied at registration. Unique: one National Identity belongs to exactly one Patient.
_Avoid_: TC, SSN, national id number

**Room**:
A free-text label naming where a Patient is placed. Rooms are not registered; any label is accepted.

**Patient Move**:
A single recorded fact that a registered Patient was placed in a Room at a point in time. It refers to the Patient by patient id only. Moves are only ever added, never edited; a Patient's history is the sequence of their moves.
_Avoid_: Location update, patient location (as a noun for one record)

**PatientMoved**:
The event announcing that a Patient Move was recorded. It carries references (the move, the Patient's id, the Room and when the move happened), never the Patient's personal details such as name or National Identity.
_Avoid_: LocationChanged, PatientLocationUpdated

### Delivery

**Subscriber**:
A service that receives every PatientMoved through its own subscription, independently of other Subscribers. Named after the service it belongs to.
_Avoid_: Consumer (for the service), listener (that is the code, not the party)

**Delivery**:
One offer of a PatientMoved to a Subscriber. It counts as successful only when one of its Call Attempts is accepted; otherwise the event is offered again as a new Delivery.
_Avoid_: Retry (for a single attempt), try

**Call Attempt**:
One call handing a PatientMoved to the Subscriber's service within a Delivery. A Delivery makes several Call Attempts, waiting a little longer before each, and fails only when all of them fail or the service rejects the event as invalid (which another call would not fix).
_Avoid_: Delivery (that is the whole offer), retry (for a single call)

**Patient Order**:
A Subscriber receives a Patient's PatientMoved events one at a time, in the order the moves were recorded. A PatientMoved that is still pending holds back that Patient's later ones, but never another Patient's.
_Avoid_: Global order (events of different Patients have no order between them)

**Pending Message**:
A PatientMoved waiting for a Subscriber: not yet successfully delivered and not set aside as a Dead Letter. It may already have had failed Deliveries.
_Avoid_: Active message, queued message

**Dead Letter**:
A PatientMoved set aside after it could not be delivered within the allowed number of Deliveries. It keeps its original content and the reason it was set aside, and stays until someone acts on it.
_Avoid_: Failed message, error, poison message (a poison message is one that can never succeed; a Dead Letter may just have met a Subscriber that was down)
