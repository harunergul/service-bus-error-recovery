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
One attempt to hand a PatientMoved to a Subscriber. It counts as successful only when the Subscriber accepts it; otherwise the event is offered again as a new Delivery.
_Avoid_: Retry (for a single attempt), try

**Dead Letter**:
A PatientMoved set aside after it could not be delivered within the allowed number of Deliveries. It keeps its original content and the reason it was set aside, and stays until someone acts on it.
_Avoid_: Failed message, error, poison message (a poison message is one that can never succeed; a Dead Letter may just have met a Subscriber that was down)
